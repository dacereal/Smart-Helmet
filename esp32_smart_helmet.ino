#include <WiFi.h>
#include <WiFiClient.h>
#include <WebServer.h>
#include <esp_camera.h>

// WiFi credentials - CHANGE THESE TO YOUR NETWORK
const char* ssid = "YourWiFiName";  // Replace with your actual WiFi name
const char* password = "YourWiFiPassword";  // Replace with your actual WiFi password

// MJPEG stream server  
WebServer server(81);
const int streamPort = 81;  // MJPEG stream port

// Drowsiness detection variables
bool isDrowsy = false;
unsigned long lastDrowsinessUpdate = 0;
const unsigned long drowsinessUpdateInterval = 1000; // Update every 1 second

// Camera configuration
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIO_GPIO_NUM      27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

void setup() {
  Serial.begin(115200);
  Serial.println("Smart Helmet ESP32 Starting...");
  
  // Initialize camera
  if (!initCamera()) {
    Serial.println("Camera initialization failed!");
    return;
  }
  
  // Connect to WiFi
  connectToWiFi();
  
  // Setup MJPEG stream server
  setupStreamServer();
  
  Serial.println("ESP32 Setup Complete!");
  Serial.print("MJPEG Stream URL: http://");
  Serial.print(WiFi.localIP());
  Serial.println(":81/stream");
}

void loop() {
  // Check WiFi connection
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected, reconnecting...");
    connectToWiFi();
    return;
  }
  
  // Handle web server requests
  server.handleClient();
  
  // Update drowsiness detection periodically
  updateDrowsinessDetection();
  
  delay(10); // Small delay for stability
}

bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIO_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  // Frame size and quality
  config.frame_size = FRAMESIZE_QVGA; // 320x240
  config.jpeg_quality = 12;
  config.fb_count = 1;
  
  // Initialize camera
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x\n", err);
    return false;
  }
  
  Serial.println("Camera initialized successfully");
  return true;
}

void connectToWiFi() {
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);
  
  WiFi.begin(ssid, password);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("WiFi connected!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println();
    Serial.println("WiFi connection failed!");
  }
}

void setupStreamServer() {
  // MJPEG stream endpoint
  server.on("/stream", HTTP_GET, handleStream);
  
  // Status endpoint for drowsiness data
  server.on("/status", HTTP_GET, handleStatus);
  
  // Test endpoint for connectivity check
  server.on("/test", HTTP_GET, handleTest);
  
  // Start server
  server.begin();
  Serial.println("MJPEG stream server started on port 81");
}

void handleStream() {
  Serial.println("MJPEG stream client connected");
  WiFiClient client = server.client();
  
  // Set longer timeout for streaming
  client.setNoDelay(true);
  
  String response = "HTTP/1.1 200 OK\r\n";
  response += "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n";
  response += "Cache-Control: no-cache\r\n";
  response += "Connection: keep-alive\r\n\r\n";
  
  Serial.println("Sending HTTP headers for MJPEG stream");
  client.print(response);
  
  int frameCount = 0;
  while (client.connected() && client.available() == -1) {
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Camera capture failed, frame: " + String(frameCount));
      esp_camera_fb_return(fb);
      delay(500);
      continue;
    }
    
    frameCount++;
    if (frameCount % 100 == 0) {
      Serial.println("Sent frame: " + String(frameCount));
    }
    
    String frameStart = "--frame\r\n";
    String contentType = "Content-Type: image/jpeg\r\n";
    String contentLength = "Content-Length: " + String(fb->len) + "\r\n\r\n";
    
    client.print(frameStart);
    client.print(contentType);
    client.print(contentLength);
    
    size_t bytesWritten = client.write(fb->buf, fb->len);
    if (bytesWritten != fb->len) {
      Serial.println("Failed to write full frame, wrote: " + String(bytesWritten) + "/" + String(fb->len));
    }
    
    client.print("\r\n");
    client.flush();
    
    esp_camera_fb_return(fb);
    delay(200); // 5 FPS - slower for stability
  }
  
  Serial.println("MJPEG stream client disconnected. Total frames sent: " + String(frameCount));
}

void handleStatus() {
  String json = "{";
  json += "\"is_drowsy\":" + String(isDrowsy ? "true" : "false") + ",";
  json += "\"timestamp\":" + String(millis()) + ",";
  json += "\"ip\":\"" + WiFi.localIP().toString() + "\"";
  json += "}";
  
  server.send(200, "application/json", json);
}

void handleTest() {
  Serial.println("Test endpoint accessed");
  String response = "ESP32 Smart Helmet Online!\n";
  response += "Uptime: " + String(millis()) + "ms\n";
  response += "WiFi IP: " + WiFi.localIP().toString() + "\n";
  response += "Free heap: " + String(esp_get_free_heap_size()) + " bytes\n";
  
  server.send(200, "text/plain", response);
}

void updateDrowsinessDetection() {
  unsigned long currentTime = millis();
  
  // Update drowsiness detection every second
  if (currentTime - lastDrowsinessUpdate >= drowsinessUpdateInterval) {
    lastDrowsinessUpdate = currentTime;
    
    // Capture frame for drowsiness detection
    camera_fb_t *fb = esp_camera_fb_get();
    if (fb) {
      // Simple drowsiness detection (replace with your actual detection logic)
      isDrowsy = detectDrowsiness(fb->buf, fb->len);
      
      Serial.print("Drowsiness detection: ");
      Serial.println(isDrowsy ? "DROWSY" : "ALERT");
      
      // Release frame buffer
      esp_camera_fb_return(fb);
    }
  }
}

bool detectDrowsiness(uint8_t* imageData, size_t imageSize) {
  // TODO: Implement your actual drowsiness detection algorithm here
  // For now, this is a simple random simulation
  
  // You can replace this with your actual detection logic
  // For example:
  // - Use OpenCV for image processing
  // - Implement eye detection algorithms
  // - Use machine learning models
  // - Analyze image brightness/contrast
  
  // Simple simulation - 20% chance of detecting drowsiness
  return (random(0, 100) < 20);
}

// Error handling functions
void handleCameraError() {
  Serial.println("Camera error detected, restarting...");
  esp_camera_deinit();
  delay(1000);
  initCamera();
}

void handleConnectionError() {
  Serial.println("Connection error, resetting connection...");
  // Reset WiFi connection
  WiFi.disconnect();
  delay(1000);
  connectToWiFi();
}

// Watchdog timer to prevent crashes
void setupWatchdog() {
  esp_task_wdt_init(30, true); // 30 second timeout
  esp_task_wdt_add(NULL);
}

void feedWatchdog() {
  esp_task_wdt_reset();
}

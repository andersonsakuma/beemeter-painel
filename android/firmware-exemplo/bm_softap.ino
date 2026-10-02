// Sketch de REFERÊNCIA do protocolo do app Colmeia Viva (android/PROTOCOLO-NOS.md).
// Não é o firmware do BeeMeter: mostra só o necessário para o celular achar o nó e ler a telemetria.
// Placa: ESP32 (Arduino core 2.x ou 3.x). Não foi testado em hardware.
#include <WiFi.h>
#include <WebServer.h>

static const char* NO_ID      = "BM-04";       // vira COL-04 no app
static const char* SENHA_WIFI = "beemeter1";   // 8 a 63 caracteres, igual em todos os nós
static const uint32_t JANELA_MS = 3UL * 60UL * 1000UL;  // SoftAP ligado por 3 min após acionar

WebServer http(80);
uint32_t seq = 0;
uint32_t ate = 0;          // millis() até quando o SoftAP fica ligado
bool ligado = false;

// Troque pelas leituras reais do nó. Sem leitura = null, nunca 0.
void telemetria() {
  String j = "{\"no\":\"" + String(NO_ID) + "\",\"fw\":\"2.0.0\",\"seq\":" + String(seq++) +
             ",\"tc1\":3455,\"tc2\":3120,\"tc3\":2980,\"lux\":21000,\"t_ext\":27,\"rh\":61,"
             "\"peso\":null,\"vbat\":3980,\"falhas\":0,\"eventos\":0}";
  http.send(200, "application/json", j);
}

void ligarAP() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(NO_ID, SENHA_WIFI, /*canal*/ 6, /*oculto*/ 0, /*max_conexoes*/ 1);
  http.on("/telemetria", HTTP_GET, telemetria);
  http.begin();
  ate = millis() + JANELA_MS;
  ligado = true;
}

void desligarAP() {
  http.stop();
  WiFi.softAPdisconnect(true);
  WiFi.mode(WIFI_OFF);
  ligado = false;
}

// Acione quando o apicultor chegar: reed switch da tampa, botão etc.
bool acionado() { return false; /* ex.: digitalRead(PINO_REED) == HIGH */ }

void setup() {}

void loop() {
  if (!ligado && acionado()) ligarAP();
  if (ligado) {
    http.handleClient();
    if ((int32_t)(millis() - ate) > 0) desligarAP();   // volta ao ciclo normal de sono
  }
}

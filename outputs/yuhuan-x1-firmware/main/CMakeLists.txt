idf_component_register(SRCS "main.cpp" "audio.cpp" "control.cpp" "ble_transport.cpp" "wifi_transport.cpp"
    INCLUDE_DIRS "."
    REQUIRES yh_core fvad esp_driver_i2s esp_driver_gpio esp_driver_spi esp_driver_rmt
             esp_adc fatfs sdmmc nvs_flash esp_wifi esp_netif esp_event esp_http_server
             bt esp_timer esp_pm esp_system json)
target_compile_features(${COMPONENT_LIB} PUBLIC cxx_std_17)

file(GLOB FVAD_SOURCES "src/*.c" "src/vad/*.c" "src/signal_processing/*.c")
idf_component_register(SRCS ${FVAD_SOURCES} INCLUDE_DIRS "include" PRIV_INCLUDE_DIRS "src")

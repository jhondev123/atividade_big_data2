#!/usr/bin/env bash
# Instala o Java 11 e o Hadoop 3.3.6 na pasta do usuario (sem sudo)
# e configura as variaveis de ambiente no ~/.bashrc.
# Pode rodar de novo: o que ja estiver instalado e pulado.
set -euo pipefail

VERSAO_HADOOP=3.3.6
JAVA_DIR="$HOME/java"
HADOOP_DIR="$HOME/hadoop"
DOWNLOADS="$HOME/instaladores"
mkdir -p "$DOWNLOADS"

# 1. Java 11 (JDK: traz o "java", que roda o Hadoop, e o "javac", que compila o job)
if [ ! -x "$JAVA_DIR/bin/javac" ]; then
    echo "[1/3] Baixando o Java 11 (Eclipse Temurin)..."
    curl -fL -o "$DOWNLOADS/jdk11.tar.gz" \
        "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse"
    mkdir -p "$JAVA_DIR"
    tar -xzf "$DOWNLOADS/jdk11.tar.gz" -C "$JAVA_DIR" --strip-components=1
else
    echo "[1/3] Java ja instalado em $JAVA_DIR"
fi

# 2. Hadoop (o arquivo oficial da Apache, conferido pelo checksum SHA-512 que ela publica)
if [ ! -x "$HADOOP_DIR/bin/hadoop" ]; then
    echo "[2/3] Baixando o Hadoop $VERSAO_HADOOP..."
    ARQUIVO="hadoop-$VERSAO_HADOOP.tar.gz"
    if [ ! -f "$DOWNLOADS/$ARQUIVO" ]; then
        curl -fL -o "$DOWNLOADS/$ARQUIVO" "https://dlcdn.apache.org/hadoop/common/hadoop-$VERSAO_HADOOP/$ARQUIVO" \
            || curl -fL -o "$DOWNLOADS/$ARQUIVO" "https://archive.apache.org/dist/hadoop/common/hadoop-$VERSAO_HADOOP/$ARQUIVO"
    fi
    curl -fsL -o "$DOWNLOADS/$ARQUIVO.sha512" \
        "https://archive.apache.org/dist/hadoop/common/hadoop-$VERSAO_HADOOP/$ARQUIVO.sha512"
    # O .sha512 da 3.3.6 cita o arquivo como "hadoop-3.3.6-RC1.tar.gz", entao comparamos so o hash
    ESPERADO=$(grep -oE '[0-9a-fA-F]{128}' "$DOWNLOADS/$ARQUIVO.sha512" | tr 'A-F' 'a-f')
    CALCULADO=$(sha512sum "$DOWNLOADS/$ARQUIVO" | cut -d' ' -f1)
    if [ "$ESPERADO" != "$CALCULADO" ]; then
        echo "ERRO: o arquivo baixado nao confere com o checksum da Apache. Apague $DOWNLOADS/$ARQUIVO e rode de novo."
        exit 1
    fi
    echo "Checksum SHA-512 do Hadoop confere com o publicado pela Apache."
    mkdir -p "$HADOOP_DIR"
    tar -xzf "$DOWNLOADS/$ARQUIVO" -C "$HADOOP_DIR" --strip-components=1
else
    echo "[2/3] Hadoop ja instalado em $HADOOP_DIR"
fi

# 3. Variaveis de ambiente: onde estao o Java e o Hadoop, e os comandos no PATH
echo "[3/3] Configurando as variaveis de ambiente..."
if ! grep -q "# Java e Hadoop" "$HOME/.bashrc"; then
    cat >> "$HOME/.bashrc" <<'FIM'

# Java e Hadoop (trabalho de Big Data)
export JAVA_HOME="$HOME/java"
export HADOOP_HOME="$HOME/hadoop"
export PATH="$JAVA_HOME/bin:$HADOOP_HOME/bin:$PATH"
FIM
fi
# O Hadoop tambem le o JAVA_HOME do proprio arquivo de configuracao dele
if ! grep -q "^export JAVA_HOME=" "$HADOOP_DIR/etc/hadoop/hadoop-env.sh"; then
    echo "export JAVA_HOME=$JAVA_DIR" >> "$HADOOP_DIR/etc/hadoop/hadoop-env.sh"
fi

export JAVA_HOME="$JAVA_DIR" HADOOP_HOME="$HADOOP_DIR" PATH="$JAVA_DIR/bin:$HADOOP_DIR/bin:$PATH"
echo
java -version
hadoop version | head -n 1
echo
echo "Pronto. Abra um terminal novo (ou rode: source ~/.bashrc) para usar java e hadoop."

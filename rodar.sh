#!/usr/bin/env bash
# Trabalho 2: funil de analises no Hadoop para criar um titulo hipotetico para a Netflix.
#
# Roda o mesmo job MapReduce 4 vezes, cada vez num recorte menor, sempre seguindo o vencedor
# da etapa anterior:  catalogo inteiro -> tipo -> generos -> pais.
# No fim mostra a ficha tecnica que os dados sugerem.
#
# Uso: ./rodar.sh   (precisa do Java e do Hadoop instalados: veja o ./instalar.sh)
set -euo pipefail
cd "$(dirname "$0")"
export LC_ALL=C                           # ordenacao igual em qualquer maquina

command -v hadoop > /dev/null && command -v javac > /dev/null \
    || { echo "ERRO: java/hadoop nao encontrados. Rode ./instalar.sh e abra um terminal novo."; exit 1; }

# etapa <nome> [-D filtro=valor ...]: roda o job no recorte; o resultado fica em saida/<nome>/part-r-00000
etapa() {
    local nome=$1; shift
    echo "      etapa $nome $*"
    hadoop jar netflix-perfil.jar "$@" netflix_titles.csv "saida/$nome" >> saida/hadoop.log 2>&1 \
        || { echo "ERRO: o job falhou. Ultimas linhas do log:"; tail -n 30 saida/hadoop.log; exit 1; }
}

# ---------- funcoes de leitura da saida do job ("dimensao  valor  quantidade") ----------

arq() { echo "saida/$1/part-r-00000"; }

total_titulos() { awk -F'\t' '$1 == "tipo" { s += $3 } END { print s }' "$(arq "$1")"; }

# valores <etapa> <dimensao> [n]: os n valores mais frequentes, desempate alfabetico
valores() {
    awk -F'\t' -v d="$2" '$1 == d { print $3 "\t" $2 }' "$(arq "$1")" | sort -t$'\t' -k1,1nr -k2,2 | head -n "${3:-5}"
}

primeiro() { valores "$1" "$2" 1 | cut -f2; }

# tabela <etapa> <dimensao> <n> <titulo>: ranking com % sobre o total de titulos do recorte
tabela() {
    echo "  $4"
    valores "$1" "$2" "$3" | awk -F'\t' -v t="$(total_titulos "$1")" \
        '{ printf "    %2d. %-42s %5d  (%4.1f%% dos titulos)\n", NR, $2, $1, 100 * $1 / t }'
}

# media e mediana de uma dimensao numerica (duracao_min, temporadas), a partir do histograma
estatisticas() {
    awk -F'\t' -v d="$2" '$1 == d { print $2 "\t" $3 }' "$(arq "$1")" | sort -t$'\t' -k1,1n | awk -F'\t' '
        { v[NR] = $1; c[NR] = $2; n += $2; soma += $1 * $2 }
        END {
            for (i = 1; i <= NR; i++) { acum += c[i]; if (acum >= n / 2) { mediana = v[i]; break } }
            printf "%d\t%.1f\t%d\n", mediana, soma / n, n
        }'
}

# filmes e series adicionados por ano (so anos com mais de 50 titulos)
por_ano() {
    awk -F'\t' '$1 == "tipo_por_ano" { split($2, p, " "); ano = p[1]; tipo = substr($2, 6)
                                       if (tipo == "Movie") f[ano] = $3; else s[ano] = $3; anos[ano] = 1 }
                END { for (a in anos) if (f[a] + s[a] > 50)
                          printf "    %s   filmes %5d   series %4d   (%2.0f%% series)\n", a, f[a], s[a], 100 * s[a] / (f[a] + s[a]) }' \
        "$(arq "$1")" | sort
}

# diretores do recorte; empate -> quem tem mais titulos no catalogo inteiro (etapa 1); depois alfabetico
diretores() {
    awk -F'\t' 'FNR == NR { if ($1 == "diretor") geral[$2] = $3; next }
                $1 == "diretor" { print $3 "\t" geral[$2] "\t" $2 }' "$(arq 1-catalogo)" "$(arq "$1")" |
        sort -t$'\t' -k1,1nr -k2,2nr -k3,3 | head -n "$2"
}

# ------------------------------------- fluxo -------------------------------------

echo "[1/3] Compilando o codigo e gerando o netflix-perfil.jar..."
rm -rf build && mkdir build
javac -encoding UTF-8 -cp "$(hadoop classpath)" -d build src/netflix/*.java          # as bibliotecas do Hadoop entram no classpath
jar cfe netflix-perfil.jar netflix.PerfilCatalogo -C build . -C src stopwords.txt

echo "[2/3] Rodando o funil de analises no Hadoop (log completo em saida/hadoop.log)..."
rm -rf saida && mkdir saida                                          # cada etapa cria a sua pasta dentro de saida/

etapa 1-catalogo
TIPO=$(primeiro 1-catalogo tipo)

etapa 2-tipo -D "netflix.filtro.tipo=$TIPO"
GENEROS=$(valores 2-tipo genero 2 | cut -f2 | paste -sd, -)

etapa 3-generos -D "netflix.filtro.tipo=$TIPO" -D "netflix.filtro.generos=$GENEROS"
PAIS=$(primeiro 3-generos pais)

etapa 4-pais -D "netflix.filtro.tipo=$TIPO" -D "netflix.filtro.generos=$GENEROS" -D "netflix.filtro.pais=$PAIS"

echo "[3/3] Resultado"
{
echo
echo "================================ ETAPA 1: CATALOGO INTEIRO ================================"
echo "  $(total_titulos 1-catalogo) titulos"
tabela 1-catalogo tipo 2 "Tipo:"
echo "  Filmes x series adicionados por ano:"
por_ano 1-catalogo
tabela 1-catalogo diretor_informado 4 "Titulos com diretor informado:"
echo "  -> escolhido: $TIPO (o tipo mais frequente)"

echo
echo "================================ ETAPA 2: SO $TIPO ================================"
echo "  $(total_titulos 2-tipo) titulos"
tabela 2-tipo genero 8 "Generos:"
tabela 2-tipo combinacao_generos 5 "Combinacoes exatas de generos:"
IFS=$'\t' read -r MED_T2 MEDIA_T2 _ < <(estatisticas 2-tipo duracao_min)
echo "  Duracao: mediana ${MED_T2} min, media ${MEDIA_T2} min"
echo "  -> escolhidos: os 2 generos mais frequentes ($GENEROS)"

echo
echo "================================ ETAPA 3: $TIPO + $GENEROS ================================"
echo "  $(total_titulos 3-generos) titulos"
tabela 3-generos pais 6 "Paises:"
echo "  -> escolhido: $PAIS (o pais mais frequente)"

echo
echo "================================ ETAPA 4: $TIPO + $GENEROS + $PAIS ================================"
echo "  $(total_titulos 4-pais) titulos"
echo "  Diretores (empate desfeito pelo total de titulos no catalogo inteiro):"
diretores 4-pais 6 | awk -F'\t' '{ printf "    %2d. %-28s %2d no recorte, %2d no catalogo\n", NR, $3, $1, $2 }'
tabela 4-pais elenco 10 "Elenco:"
tabela 4-pais classificacao 4 "Classificacao indicativa:"
IFS=$'\t' read -r MEDIANA MEDIA _ < <(estatisticas 4-pais duracao_min)
echo "  Duracao: mediana ${MEDIANA} min, media ${MEDIA} min"
tabela 4-pais mes_adicao 3 "Mes em que mais entram titulos:"
echo "  Palavras mais frequentes nas descricoes (sem stopwords):"
valores 4-pais palavra 15 | awk -F'\t' '{ printf "    %2d. %-14s %4d ocorrencias\n", NR, $2, $1 }'

echo
echo "================================ FICHA SUGERIDA PELOS DADOS ================================"
echo "  Tipo:           $TIPO"
echo "  Genero:         $GENEROS"
echo "  Pais:           $PAIS"
echo "  Duracao:        ${MEDIANA} min (mediana do recorte)"
echo "  Classificacao:  $(primeiro 4-pais classificacao)"
echo "  Diretor:        $(diretores 4-pais 1 | cut -f3)"
echo "  Elenco:         $(valores 4-pais elenco 5 | cut -f2 | paste -sd, - | sed 's/,/, /g')"
echo "  Lancamento:     $(primeiro 4-pais mes_adicao)"
echo "  Temas:          $(valores 4-pais palavra 10 | cut -f2 | paste -sd, - | sed 's/,/, /g')"
echo "============================================================================================="
} | tee saida/analise.txt

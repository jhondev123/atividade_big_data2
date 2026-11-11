package netflix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// classe de utilitários
final class Utils {

    private Utils() {
    }

    static String normalizar(String texto) {
        String t = texto.toLowerCase(Locale.ROOT);                                  
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");  // tira acentos
        return t.replaceAll("[^a-z0-9]+", " ").trim();                             
    }

    static List<String> lerCampos(String registro) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;

        for (int i = 0; i < registro.length(); i++) {
            char c = registro.charAt(i);
            if (entreAspas) {
                if (c != '"') {
                    atual.append(c);
                } else if (i + 1 < registro.length() && registro.charAt(i + 1) == '"') {
                    atual.append('"'); // "" dentro de aspas = aspas literal
                    i++;
                } else {
                    entreAspas = false;
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == ',') {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return entreAspas ? null : campos;
    }

    static Set<String> carregarStopwords() throws IOException {
        // É uma lista de stopwords em inglês da NLTK
        InputStream arquivo = Utils.class.getResourceAsStream("/stopwords.txt");
        if (arquivo == null) {
            throw new IOException("stopwords.txt nao encontrado dentro do .jar");
        }

        Set<String> stopwords = new HashSet<>();
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(arquivo, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                for (String palavra : linha.trim().split("\\s+")) {
                    if (!palavra.isEmpty()) {
                        stopwords.add(palavra.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return stopwords;
    }

    // separa os campos que são listas, como elenco, país e gêneros: "A, B, C" vira [A, B, C]
    static List<String> separarLista(String campo) {
        List<String> itens = new ArrayList<>();
        for (String item : campo.split(",")) {
            String limpo = item.replace('\n', ' ').trim();
            if (!limpo.isEmpty()) {
                itens.add(limpo);
            }
        }
        return itens;
    }
}

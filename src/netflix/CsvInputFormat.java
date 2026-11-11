package netflix;

import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;

/**
 * Entrega ao Mapper um registro CSV completo por vez, mesmo quando um campo entre aspas
 * tem quebra de linha (o TextInputFormat padrao cortaria o registro ao meio).
 */
public class CsvInputFormat extends FileInputFormat<LongWritable, Text> {

    /** Cada arquivo vai inteiro para um mapper: o 1o registro e sempre o cabecalho. */
    @Override
    protected boolean isSplitable(JobContext context, Path arquivo) {
        return false;
    }

    @Override
    public RecordReader<LongWritable, Text> createRecordReader(InputSplit split, TaskAttemptContext context) {
        return new CsvRecordReader();
    }

    static class CsvRecordReader extends RecordReader<LongWritable, Text> {
        private final LineRecordReader linhas = new LineRecordReader();
        private final LongWritable posicao = new LongWritable();
        private final Text registro = new Text();

        @Override
        public void initialize(InputSplit split, TaskAttemptContext context) throws IOException {
            linhas.initialize(split, context);
        }

        // entrega o próximo registro; junta linhas quando há quebra dentro de aspas
        @Override
        public boolean nextKeyValue() throws IOException {
            if (!linhas.nextKeyValue()) {
                return false;
            }
            posicao.set(linhas.getCurrentKey().get());
            String linha = linhas.getCurrentValue().toString();
            StringBuilder atual = new StringBuilder(linha);
            int aspas = contarAspas(linha);

            // Numero impar de aspas = campo aberto: o registro continua na proxima linha.
            while (aspas % 2 != 0 && linhas.nextKeyValue()) {
                linha = linhas.getCurrentValue().toString();
                atual.append('\n').append(linha);
                aspas += contarAspas(linha);
            }
            registro.set(atual.toString());
            return true;
        }

        private static int contarAspas(String linha) {
            int total = 0;
            for (int i = 0; i < linha.length(); i++) {
                if (linha.charAt(i) == '"') {
                    total++;
                }
            }
            return total;
        }

        @Override
        public LongWritable getCurrentKey() {
            return posicao;
        }

        @Override
        public Text getCurrentValue() {
            return registro;
        }

        @Override
        public float getProgress() throws IOException {
            return linhas.getProgress();
        }

        @Override
        public void close() throws IOException {
            linhas.close();
        }
    }
}

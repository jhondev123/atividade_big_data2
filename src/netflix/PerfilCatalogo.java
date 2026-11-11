package netflix;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.reduce.IntSumReducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class PerfilCatalogo extends Configured implements Tool {

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Uso: hadoop jar netflix-perfil.jar [-D filtros] <entrada> <saida>");
            return 2;
        }

        // filtros do recorte, passados com -D (ex.: -D netflix.filtro.tipo=Movie); sem filtro analisa o catálogo inteiro
        Configuration conf = getConf();
        String recorte = "tipo=" + conf.get(PerfilMapper.FILTRO_TIPO, "*")
                + " generos=" + conf.get(PerfilMapper.FILTRO_GENEROS, "*")
                + " pais=" + conf.get(PerfilMapper.FILTRO_PAIS, "*");

        Job job = Job.getInstance(conf, "Netflix - perfil do catalogo (" + recorte + ")");
        job.setJarByClass(PerfilCatalogo.class);

        // define qual a classe que le as linhas e transforma em um registro
        job.setInputFormatClass(CsvInputFormat.class);

        // faz o mapeamento de cada título do recorte em (dimensão, valor), como tipo, gênero, país, elenco e palavras
        job.setMapperClass(PerfilMapper.class);

        // faz um agrupamento das chaves que são iguais somando as mesmas para reduzir o número de chaves repetidas
        // IntSumReducer é uma classe do próprio Hadoop que só soma, não precisamos escrever um combiner
        job.setCombinerClass(IntSumReducer.class);

        // classe que faz o processamento dos registros já mapeados e enxutos, somando cada (dimensão, valor)
        job.setReducerClass(IntSumReducer.class);
        // quantidades de tasks usadas para processar os dados, usamos 1 para sair um arquivo só de saída
        // aqui cada chave é independente, então daria para usar mais tasks e juntar os arquivos de saída depois
        job.setNumReduceTasks(1);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new Configuration(), new PerfilCatalogo(), args));
    }
}

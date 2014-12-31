package storm.kafka;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.task.ShellBolt;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class KafkaSpoutTestTopology {
    public static final Logger LOG1 = LoggerFactory.getLogger(KafkaSpoutTestTopology.class);
    
    public static class SplitSentence extends ShellBolt implements IRichBolt {

	    public SplitSentence() {
	      super("python", "splitsentence.py");
	    }

	    @Override
	    public void declareOutputFields(OutputFieldsDeclarer declarer) {
	    	declarer.declare(new Fields("word"));
	    }

	    @Override
	    public Map<String, Object> getComponentConfiguration() {
	      return null;
	    }
	}
    
    public static class WordCount extends BaseBasicBolt {
        Map<String, Integer> counts = new HashMap<String, Integer>();

        @Override
        public void execute(Tuple tuple, BasicOutputCollector collector) {
          String word = tuple.getString(0);
          Integer count = counts.get(word);
          if (count == null)
            count = 0;
          count++;
          counts.put(word, count);
          collector.emit(new Values(word, count));
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
          declarer.declare(new Fields("word", "count"));
        }
     }
    
	public static class PrinterBolt extends BaseBasicBolt {
		  		
		@Override
		public void execute(Tuple tuple, BasicOutputCollector collector) {
			LOG1.info(tuple.toString());
		}

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
		}

	}

    private final BrokerHosts brokerHosts;

    public KafkaSpoutTestTopology(String kafkaZookeeper) {
        brokerHosts = new ZkHosts(kafkaZookeeper);
    }

    public StormTopology buildTopology() {
    	String topicName = "stormone";
        SpoutConfig kafkaConfig = new SpoutConfig(brokerHosts, topicName, "/" + topicName, UUID.randomUUID().toString());
        kafkaConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        kafkaConfig.forceFromStart = false;
        kafkaConfig.zkPort = 2181;
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("spout", new KafkaSpout(kafkaConfig));
        builder.setBolt("split", new SplitSentence(), 2).shuffleGrouping("spout");
        //builder.setBolt("count", new WordCount(), 2).fieldsGrouping("split", new Fields("word"));
        builder.setBolt("print", new PrinterBolt(), 2).shuffleGrouping("split");
        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception {
    	String kafkaZk = "node02:2181,node03:2181,node04:2181/kafka";
        KafkaSpoutTestTopology kafkaSpoutTestTopology = new KafkaSpoutTestTopology(kafkaZk);
        Config config = new Config();
        config.put(Config.TOPOLOGY_TRIDENT_BATCH_EMIT_INTERVAL_MILLIS, 2000);
        

        StormTopology stormTopology = kafkaSpoutTestTopology.buildTopology();
        if (args != null && args.length > 1) {
            String name = args[0];
            String dockerIp = args[1];
            config.setNumWorkers(2);
            //config.setMaxTaskParallelism(5);
            config.put(Config.NIMBUS_HOST, dockerIp);
            //config.put(Config.NIMBUS_THRIFT_PORT, 6627);
            //config.put(Config.STORM_ZOOKEEPER_PORT, 2181);
            //config.put(Config.STORM_ZOOKEEPER_SERVERS, Arrays.asList(zks));
            StormSubmitter.submitTopology(name, config, stormTopology);
        } else {
            config.setNumWorkers(2);
            config.setMaxTaskParallelism(2);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("kafka", config, stormTopology);
        }
    }
}

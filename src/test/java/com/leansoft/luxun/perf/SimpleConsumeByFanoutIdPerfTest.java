package com.leansoft.luxun.perf;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.leansoft.luxun.common.exception.TopicNotExistException;
import com.leansoft.luxun.consumer.SimpleConsumer;
import com.leansoft.luxun.message.Message;
import com.leansoft.luxun.message.MessageList;
import com.leansoft.luxun.producer.IProducer;
import com.leansoft.luxun.producer.ProducerConfig;
import com.leansoft.luxun.producer.ProducerData;
import com.leansoft.luxun.serializer.Decoder;
import com.leansoft.luxun.serializer.StringDecoder;
import com.leansoft.luxun.serializer.StringEncoder;
import com.leansoft.luxun.server.LuxunServer;
import com.leansoft.luxun.server.ServerConfig;
import com.leansoft.luxun.utils.TestUtils;
import com.leansoft.luxun.utils.Utils;

public class SimpleConsumeByFanoutIdPerfTest {
	private int port = 9092;
	private int brokerId = 0;
	private LuxunServer server = null;
	private String brokerList = brokerId + ":127.0.0.1:" + port;
	
	@Before
	public void setup() {
		Properties props1 = TestUtils.createBrokerConfig(brokerId, port);
		ServerConfig config1 = new ServerConfig(props1);
		server = TestUtils.createServer(config1);
	}
	
	@After
	public void clean() throws Exception {
		server.close();
		
		Utils.deleteDirectory(new File(server.config.getLogDir()));
		Thread.sleep(500);
	}
	
	// configurable parameters
	//////////////////////////////////////////////////////////////////
	private static int loop = 5;
	private static int totalItemCount = 100000;
	private static int producerNum = 4;
	private static int consumerGroupANum = 2;
	private static int consumerGroupBNum = 4;
	private static int messageLength = 1024;
	//////////////////////////////////////////////////////////////////
	
	private static enum Status {
		ERROR,
		SUCCESS
	}
	
	private static class Result {
		Status status;
		long duration;
	}
	
	private static final AtomicInteger producingItemCount = new AtomicInteger(0);
    
    private static class ProducerThread extends Thread {
		private final CountDownLatch latch;
		private final Queue<Result> resultQueue;
		private final IProducer<String, String> stringProducer;
		private final String topic;
		private final String rndString = TestUtils.randomString(messageLength);
		
		public ProducerThread(CountDownLatch latch, Queue<Result> resultQueue, IProducer<String, String> stringProducer, String topic) {
			this.latch = latch;
			this.resultQueue = resultQueue;
			this.stringProducer = stringProducer;
			this.topic = topic;
		}
		
		public void run() {
			Result result = new Result();
			try {
				latch.countDown();
				latch.await();
				
				long start = System.currentTimeMillis();
				while(true) {
					int count = producingItemCount.incrementAndGet();
					if(count > totalItemCount) break;
					stringProducer.send(new ProducerData<String, String>(topic, rndString));
				}
				long end = System.currentTimeMillis();
				result.status = Status.SUCCESS;
				result.duration = end - start;
			} catch (Exception e) {
				e.printStackTrace();
				result.status = Status.ERROR;
			}
			resultQueue.offer(result);
		}	
    }
    
	// sequential consumer can work concurrently with producer
	private static class SequentialConsumerThread extends Thread {
		private final CountDownLatch latch;
		private final Queue<Result> resultQueue;
		private final SimpleConsumer simpleConsumer;
		private final String topic;
		private final Decoder<String> stringDecoder = new StringDecoder();
		private final String fanoutId;
		private final AtomicInteger itemCount;
		
		
		public SequentialConsumerThread(CountDownLatch latch, Queue<Result> resultQueue, String fanoutId, 
				SimpleConsumer simpleConsumer, String topic, AtomicInteger itemCount) {
			this.latch = latch;
			this.resultQueue = resultQueue;
			this.simpleConsumer = simpleConsumer;
			this.topic = topic;
			this.fanoutId = fanoutId;
			this.itemCount = itemCount;
		}
		
		public void run() {
			Result result = new Result();
			try {
				latch.countDown();
				latch.await();
				
				long start = System.currentTimeMillis();
				while (itemCount.get() < totalItemCount) {
					try {
						List<MessageList> listOfMessageList = simpleConsumer.consume(topic, fanoutId, 10000);
						if (listOfMessageList.size() == 0) {
							Thread.sleep(20); // no item to consume yet, just wait a moment
						}
						for(MessageList messageList : listOfMessageList) {
							for(Message message : messageList) {
								@SuppressWarnings("unused")
								String item = stringDecoder.toEvent(message);
								itemCount.incrementAndGet();
							}
						}
					} catch (TopicNotExistException ex) {
						Thread.sleep(200);// wait the producer to register the topic in broker
					}
					
				}
				long end = System.currentTimeMillis();
				result.status = Status.SUCCESS;
				result.duration = end - start;
			} catch (Exception e) {
				e.printStackTrace();
				result.status = Status.ERROR;
			}
			resultQueue.offer(result);
		}
	}
	
	public void doRunMixed(int round) throws Exception {
		//prepare
		CountDownLatch allLatch = new CountDownLatch(producerNum + consumerGroupANum + consumerGroupBNum);
		@SuppressWarnings("unchecked")
		IProducer<String, String>[] producers = new IProducer[producerNum];
		SimpleConsumer[] groupAConsumers = new SimpleConsumer[consumerGroupANum];
		SimpleConsumer[] groupBConsumers = new SimpleConsumer[consumerGroupBNum];
		BlockingQueue<Result> producerResults = new LinkedBlockingQueue<Result>();
		BlockingQueue<Result> consumerResults = new LinkedBlockingQueue<Result>();
		String topic = "load-test002-" + round;
		
		long start = System.currentTimeMillis();
		//run testing
		for(int i = 0; i < producerNum; i++) {
			Properties props = new Properties();
			props.put("serializer.class", StringEncoder.class.getName());
			props.put("broker.list", this.brokerList);
			ProducerConfig config = new ProducerConfig(props);
			IProducer<String, String> stringProducer = new com.leansoft.luxun.producer.Producer<String, String>(config);
			producers[i] = stringProducer;
			ProducerThread p = new ProducerThread(allLatch, producerResults, stringProducer, topic);
			p.start();
		}
		
		AtomicInteger groupAItemCount = new AtomicInteger(0);
		for(int i = 0; i < consumerGroupANum; i++) {
			SimpleConsumer simpleConsumer = new SimpleConsumer("127.0.0.1", 9092, 60000);
			groupAConsumers[i] = simpleConsumer;
			SequentialConsumerThread c = new SequentialConsumerThread(allLatch, consumerResults, "group-a", simpleConsumer, topic, groupAItemCount);
			c.start();
		}
		
		AtomicInteger groupBItemCount = new AtomicInteger(0);
		for(int i = 0; i < consumerGroupBNum; i++) {
			SimpleConsumer simpleConsumer = new SimpleConsumer("127.0.0.1", 9092, 60000);
			groupBConsumers[i] = simpleConsumer;
			SequentialConsumerThread c = new SequentialConsumerThread(allLatch, consumerResults, "group-b", simpleConsumer, topic, groupBItemCount);
			c.start();
		}
		
		long totalProducingTime = 0;
		long totalConsumingTime = 0;
		
		//verify
		for(int i = 0; i < producerNum; i++) {
			Result result = producerResults.take();
			assertEquals(result.status, Status.SUCCESS);
			totalProducingTime += result.duration;
		}
		
		for(int i = 0; i < consumerGroupANum + consumerGroupBNum; i++) {
			Result result = consumerResults.take();
			assertEquals(result.status, Status.SUCCESS);
			totalConsumingTime += result.duration;
		}
		
		long end = System.currentTimeMillis();
		
		System.out.println("-----------------------------------------------");
		
		System.out.println("Total item count = " + totalItemCount);
		System.out.println("Producer thread number = " + producerNum);
		System.out.println("Consumer thread number = " + (consumerGroupANum + consumerGroupBNum));
		System.out.println("Item message length = " + messageLength + " bytes");
		System.out.println("Total test time = " + (end - start) + " ms.");
		System.out.println("Total producing time = " + totalProducingTime + " ms.");
		System.out.println("Average producing time = " + totalProducingTime / producerNum + " ms.");
		System.out.println("Total consuming time = " + totalConsumingTime + " ms.");
		System.out.println("Average consuming time = " + totalConsumingTime / (consumerGroupANum + consumerGroupBNum) + " ms.");
		double throughput = (totalItemCount * messageLength * 1.0) / (1024 * 1024) / ((end - start) / 1000.0); 
		System.out.println("Throughput = " + throughput + " MB/s");
		System.out.println("-----------------------------------------------");
		
		// closing
		for(int i = 0; i < producerNum; i++) {
			producers[i].close();
		}
		for(int i = 0; i < consumerGroupANum; i++) {
			groupAConsumers[i].close();
		}
		for(int i = 0; i < consumerGroupBNum; i++) {
			groupBConsumers[i].close();
		}
	}
	
	@Test
	public void runTest() throws Exception {
		
		System.out.println("Load test begin ...");
		
		for(int i = 0; i < loop; i++) {
			System.out.println("[doRunMixed] round " + (i + 1) + " of " + loop);
			this.doRunMixed(i);
			
			// reset
			producingItemCount.set(0);
		}
		
		System.out.println("Load test finished successfully.");
	}
}

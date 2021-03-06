package bgu.spl.mics.application.services;

import bgu.spl.mics.MessageBusImpl;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.objects.GPU;
import bgu.spl.mics.application.objects.Model;
import bgu.spl.mics.application.objects.Model.Status;
import bgu.spl.mics.example.messages.TestModelEvent;
import bgu.spl.mics.example.messages.TickBroadcast;
import bgu.spl.mics.example.messages.TrainModelEvent;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * GPU service is responsible for handling the
 * {@link TrainModelEvent} and {@link TestModelEvent},
 * This class may not hold references for objects which it is not responsible for.
 *
 * You can add private fields and public methods to this class.
 * You MAY change constructor signatures and even add new public constructors.
 */
public class GPUService extends MicroService {
	private GPU myGpu;
	private MessageBusImpl bus=MessageBusImpl.getInstance();
	private TrainModelEvent inProgress=null;
	private Queue<TrainModelEvent> gym=new LinkedBlockingQueue<TrainModelEvent>();
    public GPUService(String name, GPU gpu) {
        super(name);
        this.myGpu=gpu;
    }

    @Override
    protected void initialize() {
        // TODO Implement this
    	subscribeBroadcast(TickBroadcast.class,(TickBroadcast tick)->{
    											if(tick.getTime()==-1){//need to end messageloop;
													myGpu.kill();
													terminate();}
												else{
													//System.out.println("Tick number"+tick.getTime());
													boolean var=myGpu.trainModel();
    											if(var) {
													inProgress.getModel().setStatus(Status.Trained);
													Model m=inProgress.getModel();
    												TrainModelEvent trained=new TrainModelEvent(m);
    												complete(inProgress, trained);
													System.out.println("lvl up"+inProgress.getModel().getName());
    												if(!gym.isEmpty()) {
    													inProgress=gym.remove();
    													Thread t2 = new Thread(()->{myGpu.divideToBatches(inProgress.getModel());
															System.out.println("started training of "+inProgress.getModel().getName());
															inProgress.getModel().setStatus(Status.Training);
        													myGpu.sendData();});//he is responsible of sending data to cluster.
        												t2.start();
    												}}
    												}
    	});//controls what happens when we get a tick.
    	subscribeEvent(TrainModelEvent.class, (TrainModelEvent trainee)->{
    											gym.add(trainee);
    											if(inProgress==null || inProgress.getModel().getStatus()==Status.Trained ||inProgress.getModel().getStatus()==Status.Tested ) {
    												this.inProgress=gym.remove();
    												Thread t1 = new Thread(()->{myGpu.divideToBatches(inProgress.getModel());
    													System.out.println("started training of "+inProgress.getModel().getName());
														inProgress.getModel().setStatus(Status.Training);
    													myGpu.sendData();});//he is responsible of sending data to cluster.
    												t1.start();}
    																		
    	});//controls what happens when you get tme.
    	subscribeEvent(TestModelEvent.class, (TestModelEvent testee)->{
    										Model m=myGpu.testModel(testee.getModel());
    										m.setStatus(Status.Tested);
											System.out.println("test over for "+testee.getModel().getName());
    										TestModelEvent tested=new TestModelEvent(m);
    										complete(testee, tested);
    	});

    }
}

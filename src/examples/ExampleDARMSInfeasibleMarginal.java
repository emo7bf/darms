package examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.AttackMethod;
import models.DARMSModel;
import models.Flight;
import models.PostScreeningResource;
import models.PureStrategy;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;
import utilities.DARMSPureStrategySampler;

public class ExampleDARMSInfeasibleMarginal{
	public static void main(String[] args){
		try{
			DARMSModel exampleModel = constructExampleModel();
			
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>  exampleMarginalStrategy = constructExampleMarginalStrategy(exampleModel);
			
			DARMSPureStrategySampler exampleSampler = new DARMSPureStrategySampler(exampleModel, exampleMarginalStrategy);
			
			PureStrategy p = exampleSampler.samplePureStrategy();
			
			System.out.println(p);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static DARMSModel constructExampleModel() throws Exception{
		//Adversary Distribution
		RiskCategory c = new RiskCategory("Screenee");
		
		Map<RiskCategory, Double> adversaryDistribution = new HashMap<RiskCategory, Double>();
		adversaryDistribution.put(c, 1.0);
		
		
		//Flights
		List<Flight> flightList = new ArrayList<Flight>();
		
		Map<RiskCategory, Integer> distribution = new HashMap<RiskCategory, Integer>();
		distribution.put(c, 12);
		
		Flight f = new Flight("Flight1", Flight.FlightType.DOMESTIC, 240, distribution);
		f.setPayoffs(-1, 0, 1, 1);
		
		flightList.add(f);
		
		//AttackMethods
		List<AttackMethod> attackMethods = new ArrayList<AttackMethod>();
		attackMethods.add(new AttackMethod("Explosive"));
		
		//Screening Resources
		Map<ScreeningResource, Integer> screeningResources = new HashMap<ScreeningResource, Integer>();
		
		Map<Integer, ScreeningResource> resourceIndexMap = new HashMap<Integer, ScreeningResource>();
		
		for(int i = 1; i <= 10; i++){
			ScreeningResource r = new ScreeningResource("R" + i, 8, 0);
			
			screeningResources.put(r, 1);
			resourceIndexMap.put(i, r);
		}
		
		//Screening Operations
		List<ScreeningOperation> screeningOperations = new ArrayList<ScreeningOperation>();
		
		for(int i = 0; i < 5; i++){
			Set<ScreeningResource> o = new HashSet<ScreeningResource>();
			
			int base = 2 * i; 
			
			for(int j = 1; j <= 6; j++){
				
				int index = base + j;
				
				if(index > 10){
					index = index % 10;
				}
				
				o.add(resourceIndexMap.get(index));
			}
			
			screeningOperations.add(new ScreeningOperation(o));
		}
		
		Map<PostScreeningResource, Integer> postScreeningResources = new HashMap<PostScreeningResource, Integer>();
		
		boolean flightByFlight = true;
		int shiftStartTime = 240;
		int shiftDuration = 60;
		int timeGranularity = 60;
		
		DARMSModel model = new DARMSModel(flightList, 
				adversaryDistribution,
				attackMethods,
				screeningOperations,
				screeningResources, 
				postScreeningResources,
				flightByFlight,
				shiftStartTime,
				shiftDuration,
				timeGranularity);
		
		model.calculateTemporalPassengerDistributions();
		model.setPassengerDistribution();
		model.setPayoffStructure();
		
		return model;
	}
	
	public static Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> constructExampleMarginalStrategy(DARMSModel model){
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginalStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : model.getTimeWindows()){
			marginalStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : model.getFlights(t)){
				marginalStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : f.getPassengerDistribution().keySet()){
					marginalStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
				
					int index = 1;
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						if(index < 5){
							marginalStrategy.get(t).get(f).get(c).put(o, 8.0 / 3.0);
						}
						else{
							marginalStrategy.get(t).get(f).get(c).put(o, 4.0 / 3.0);
						}
						
						index++;
					}
				}
			}
		}

		return marginalStrategy;
	}
}
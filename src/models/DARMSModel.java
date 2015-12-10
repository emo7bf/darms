package models;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.distribution.NormalDistribution;

import utilities.DARMSInstanceGenerator;

public class DARMSModel {
	private List<Flight> flights;
	private Map<RiskCategory, Double> adversaryDistribution;
	private List<ScreeningOperation> screeningOperations;
	private Map<ScreeningResource, Integer> screeningResources;
	private Map<PostScreeningResource, Integer> postScreeningResources;
	private List<AttackMethod> attackMethods;
	private ResourceFines  resourceFines;
	private boolean flightByFlight;
	private int shiftStartTime;
	private int shiftDuration;
	private int timeGranularity;

	private List<Integer> timeWindows;
	
	private Map<Integer, List<Flight>> flightMap;
	
	public static final double EPSILON = 1e-6;
	
	private PassengerDistribution passengerDistribution;
	private PayoffStructure payoffStructure;

	public DARMSModel(List<Flight> flights, 
			Map<RiskCategory, Double> adversaryDistribution,
			List<AttackMethod> attackMethods,
			List<ScreeningOperation> screeningOperations,
			Map<ScreeningResource, Integer> screeningResources,
			Map<PostScreeningResource, Integer> postScreeningResources,
			boolean flightByFlight,
			int shiftStartTime,
			int shiftDuration,
			int timeGranularity,
			String fineDist,
			double fineMin,
			double fineMax,
			int numberTests,
			int thisTest) throws Exception{
		this.flights = flights;
		this.adversaryDistribution = adversaryDistribution;
		this.attackMethods = attackMethods;
		this.screeningOperations = screeningOperations;
		this.screeningResources = screeningResources;
		this.postScreeningResources = postScreeningResources;
		this.flightByFlight = flightByFlight;
		this.shiftStartTime = shiftStartTime;
		this.shiftDuration = shiftDuration;
		this.timeGranularity = timeGranularity;
		
		for(ScreeningOperation o : this.screeningOperations){
			for(ScreeningResource r : o.getResources()){
				if(!this.screeningResources.containsKey(r)){
					this.screeningResources.put(r, 0);
				}
			}
		}
		
		timeWindows = new ArrayList<Integer>(); 
		
		for(int i = 0; i < shiftDuration / timeGranularity; i++){
			timeWindows.add(shiftStartTime + (timeGranularity * i));
		}
		
		this.setResourceFines( fineDist, fineMin, fineMax, numberTests, thisTest );
	}
	
	public void calculateTemporalPassengerDistributions(){
		NormalDistribution domesticDistribution = new NormalDistribution(-90, 30);
		NormalDistribution internationalDistribution = new NormalDistribution(-120, 40);
		NormalDistribution distribution = null;
		
		flightMap = new HashMap<Integer, List<Flight>>();
		
		for(Flight f : flights){
			Map<RiskCategory, Integer> passengerDistribution = f.getPassengerDistribution();
			
			Map<Integer, Map<RiskCategory, Integer>> temporalPassengerDistribution = new HashMap<Integer, Map<RiskCategory, Integer>>();
			
			if(f.getFlightType() == Flight.FlightType.DOMESTIC){
				distribution = domesticDistribution;
			}
			else if(f.getFlightType() == Flight.FlightType.INTERNATIONAL){
				distribution = internationalDistribution;
			}
			
			for(RiskCategory c : passengerDistribution.keySet()){
				Map<Double, Set<Integer>> modMap = new HashMap<Double, Set<Integer>>();
				int passengersAssigned = 0;
				
				for(int t = 0; t < timeWindows.size(); t++){
					double prob;
					
					if(timeWindows.size() == 1){
						prob = distribution.cumulativeProbability(timeWindows.get(0) + timeGranularity - f.getDepartureTime());
					}
					else if(t == 0){
						prob = distribution.cumulativeProbability(timeWindows.get(t + 1) - f.getDepartureTime());
					}
					else if(timeWindows.get(t) < f.getDepartureTime() && t == timeWindows.size() - 1){
						double prob1 = distribution.cumulativeProbability(timeWindows.get(t) - f.getDepartureTime());
						
						prob = 1.0 - prob1;
					}
					else if(timeWindows.get(t) < f.getDepartureTime() && f.getDepartureTime() <= timeWindows.get(t + 1)){
						double prob1 = distribution.cumulativeProbability(timeWindows.get(t) - f.getDepartureTime());
						
						prob = 1.0 - prob1;
					}
					else if(timeWindows.get(t) < f.getDepartureTime()){
						double prob1 = distribution.cumulativeProbability(timeWindows.get(t) - f.getDepartureTime());
						double prob2 = distribution.cumulativeProbability(timeWindows.get(t + 1) - f.getDepartureTime());
						
						prob = prob2 - prob1;
					}
					else{
						continue;
					}
					
					double numPassengers = passengerDistribution.get(c) * prob;
				
					if(!modMap.containsKey(numPassengers % 1.0)){
						modMap.put(numPassengers % 1.0, new HashSet<Integer>());
					}
					
					modMap.get(numPassengers % 1.0).add(timeWindows.get(t));
						
					if(!temporalPassengerDistribution.containsKey(timeWindows.get(t))){
						temporalPassengerDistribution.put(timeWindows.get(t), new HashMap<RiskCategory, Integer>());
					}
						
					temporalPassengerDistribution.get(timeWindows.get(t)).put(c, (int)numPassengers);
						
					passengersAssigned += (int)numPassengers;
				}
				
				List<Double> modList = new ArrayList<Double>(modMap.keySet());
				
				Collections.sort(modList);
				Collections.reverse(modList);
				
				for(double modValue : modList){
					for(Integer timeWindow : modMap.get(modValue)){
						while(passengersAssigned < passengerDistribution.get(c)){
							int currentlyAssigned = temporalPassengerDistribution.get(timeWindow).get(c);
							
							temporalPassengerDistribution.get(timeWindow).put(c, currentlyAssigned + 1);
							passengersAssigned++;
						}
					}
				}
			}
			
			Set<Integer> removeableTimeWindows = new HashSet<Integer>();
			
			for(int timeWindow : temporalPassengerDistribution.keySet()){
				boolean removeTimeWindow = true;
				
				for(RiskCategory c : temporalPassengerDistribution.get(timeWindow).keySet()){
					if(temporalPassengerDistribution.get(timeWindow).get(c) > 0){
						removeTimeWindow = false;
						break;
					}
				}
				
				if(removeTimeWindow){
					removeableTimeWindows.add(timeWindow);
				}	
			}
			
			for(int timeWindow : removeableTimeWindows){
				temporalPassengerDistribution.remove(timeWindow);
			}
			
			f.setTemporalPassengerDistribution(temporalPassengerDistribution);
			
			for(int t : temporalPassengerDistribution.keySet()){
				for(RiskCategory c : temporalPassengerDistribution.get(t).keySet()){
					if(temporalPassengerDistribution.get(t).get(c) > 0){
						if(!flightMap.containsKey(t)){
							flightMap.put(t, new ArrayList<Flight>());
						}
							
						flightMap.get(t).add(f);
						break;
					}
				}
			}
		}
	}
	
	public List<ScreeningOperation> getScreeningOperations(){
		return screeningOperations;
	}
	
	public Map<ScreeningResource, Integer> getScreeningResources(){
		return screeningResources;
	}
	
	public void setScreeningResources(Map<ScreeningResource, Integer> screeningResources){
		this.screeningResources = screeningResources;
	}
	
	public Map<PostScreeningResource, Integer> getPostScreeningResources(){
		return postScreeningResources;
	}
	
	public void setPostScreeningResources(Map<PostScreeningResource, Integer> postScreeningResources){
		this.postScreeningResources = postScreeningResources;
	}
	
	public Map<RiskCategory, Double> getAdversaryDistribution(){
		return adversaryDistribution;
	}
	
	public void setAdversaryDistribution(Map<RiskCategory, Double> adversaryDistribution){
		this.adversaryDistribution = adversaryDistribution;
	}
	
	public Map<Integer, Map<ScreeningResource, Double>> getResourceFines(){
		return resourceFines.getFines();
	}
	
	public void setResourceFines(String dist, double fmin, double fmax, int fnumTests, int thisTest) throws Exception{
		this.resourceFines = new ResourceFines(thisTest);
		this.resourceFines.generateFines(dist, fmin, fmax, fnumTests, thisTest, this);
		
	}
	
	public List<Flight> getFlights(){
		return flights;
	}
	
	public List<Flight> getFlights(int timeWindow){
		return flightMap.get(timeWindow);
	}
	
	public void setFlights(List<Flight> flights){
		this.flights = flights;
	}
	
	public List<AttackMethod> getAttackMethods(){
		return attackMethods;
	}
	
	public boolean flightByFlight(){
		return flightByFlight;
	}
	
	public List<Integer> getTimeWindows(){
		// List<Integer> tw = new ArrayList<Integer>(flightMap.keySet());
		
		Collections.sort(timeWindows);
		
		return timeWindows;
	}
	
	public Map<Integer, Map<ScreeningResource, Integer>> getScreeningResourceCapacities(){
		Map<Integer, Map<ScreeningResource, Integer>> screeningResourceCapacities = new HashMap<Integer, Map<ScreeningResource, Integer>>();
		
		for(int t : getTimeWindows()){
			Map<ScreeningResource, Integer> p = new HashMap<ScreeningResource, Integer>();
			
			for(ScreeningResource r : screeningResources.keySet()){
				p.put(r, r.capacity() * screeningResources.get(r));
			}
			
			screeningResourceCapacities.put(t, p);
		}
		
		return screeningResourceCapacities;
	}
	
	public void setPassengerDistribution(){
		Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> dist = new HashMap<Integer, Map<Flight, Map<RiskCategory, Integer>>>();
		
		for(int t : flightMap.keySet()){
			dist.put(t, new HashMap<Flight, Map<RiskCategory, Integer>>());
			
			for(Flight f : flightMap.get(t)){
				Map<Integer, Map<RiskCategory, Integer>> temporalDistribution = f.getTemporalPassengerDistribution();
				
				dist.get(t).put(f, new HashMap<RiskCategory, Integer>());
				
				for(RiskCategory c : temporalDistribution.get(t).keySet()){
					dist.get(t).get(f).put(c, temporalDistribution.get(t).get(c));
				}
			}
		}
		
		passengerDistribution = new PassengerDistribution(dist);
	}
	
	public PassengerDistribution getPassengerDistribution(){
		return passengerDistribution;
	}
	
	public List<PassengerDistribution> getRandomizedPassengerDistributions(int numDistributions){
		List<PassengerDistribution> passengerDistributionList = new ArrayList<PassengerDistribution>();
		
		Random rand = new Random();
		
		for(int i = 0; i < numDistributions; i++){
			Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> passengerDistribution = new HashMap<Integer, Map<Flight, Map<RiskCategory, Integer>>>();
			
			for(Flight f : flights){
				Map<Integer, Map<RiskCategory, Integer>> temporalDistribution = f.getTemporalPassengerDistribution();
				Map<RiskCategory, Integer> distribution = f.getPassengerDistribution();
				
				for(RiskCategory c : distribution.keySet()){
					Map<Integer, Double> probMap = new HashMap<Integer, Double>();
					Map<Double, Set<Integer>> modMap = new HashMap<Double, Set<Integer>>();
					
					double totalProb = 0.0;
					
					for(int t : temporalDistribution.keySet()){
						double prob = rand.nextDouble();
						
						probMap.put(t, prob);
						
						totalProb += prob;
					}
					
					int passengersAssigned = 0;
					
					for(int t : temporalDistribution.keySet()){						
						if(!passengerDistribution.containsKey(t)){
							passengerDistribution.put(t, new HashMap<Flight, Map<RiskCategory, Integer>>());
						}
						
						if(!passengerDistribution.get(t).containsKey(f)){
							passengerDistribution.get(t).put(f, new HashMap<RiskCategory, Integer>());
						}
						
						double numPassengers = distribution.get(c) * probMap.get(t) / totalProb;
						double mod = numPassengers % 1.0;
						
						if(!modMap.containsKey(mod)){
							modMap.put(mod, new HashSet<Integer>());
						}
						
						modMap.get(mod).add(t);
						
						passengerDistribution.get(t).get(f).put(c, (int)numPassengers);
						
						passengersAssigned += (int)numPassengers;
					}
					
					List<Double> modList = new ArrayList<Double>(modMap.keySet());
					
					Collections.sort(modList);
					Collections.reverse(modList);
					
					for(double modValue : modList){
						for(Integer timeWindow : modMap.get(modValue)){
							while(passengersAssigned < distribution.get(c)){
								int currentlyAssigned = passengerDistribution.get(timeWindow).get(f).get(c);
								
								passengerDistribution.get(timeWindow).get(f).put(c, currentlyAssigned + 1);
								passengersAssigned++;
							}
						}
					}
				}
			}
			
			passengerDistributionList.add(new PassengerDistribution(passengerDistribution));
		}
		
		return passengerDistributionList;
	}
	
	public List<AdversaryDistribution> getRandomizedAdversaryDistributions(int numDistributions){
		List<AdversaryDistribution> adversaryDistributionList = new ArrayList<AdversaryDistribution>();
		
		Random rand = new Random();
		
		List<RiskCategory> riskCategoryList =  new ArrayList<RiskCategory>(adversaryDistribution.keySet());
		
		Collections.sort(riskCategoryList);
		
		for(int i = 0; i < numDistributions; i++){
			Map<RiskCategory, Double> adversaryDistribution = new HashMap<RiskCategory, Double>();
			
			double previousProb = 1.0;
			double totalProb = 0.0;
			
			for(RiskCategory c : riskCategoryList){
				double prob = rand.nextDouble();
				double currentProb = prob * previousProb;
			
				adversaryDistribution.put(c, currentProb);
				
				totalProb += currentProb;
				
				previousProb = currentProb;
			}
			
			for(RiskCategory c : riskCategoryList){
				adversaryDistribution.put(c, adversaryDistribution.get(c) / totalProb);
			}
			
			adversaryDistributionList.add(new AdversaryDistribution(adversaryDistribution));
		}
		
		return adversaryDistributionList;
	}
	
	public void setPayoffStructure(){
		Map<Flight, Integer> defCovMap = new HashMap<Flight, Integer>();
		Map<Flight, Integer> defUncovMap = new HashMap<Flight, Integer>();
		Map<Flight, Integer> attCovMap = new HashMap<Flight, Integer>();
		Map<Flight, Integer> attUncovMap = new HashMap<Flight, Integer>();
		
		for(Flight f : flights){
			defCovMap.put(f, f.getDefCovPayoff()* 10000);
			defUncovMap.put(f, f.getDefUncovPayoff()* 10000);
			attCovMap.put(f, f.getAttCovPayoff()* 10000);
			attUncovMap.put(f, f.getAttUncovPayoff()* 10000);
		}
		
		payoffStructure = new PayoffStructure(defCovMap, defUncovMap, attCovMap, attUncovMap);
	}
	
	public PayoffStructure getPayoffStructure(){
		return payoffStructure;
	}
	
	public List<PayoffStructure> getRandomizedPayoffStructures(int numDistributions, double heterogeneity){
		List<PayoffStructure> flightValueDistributionList = new ArrayList<PayoffStructure>();
		
		Random rand = new Random();
		
		int defUncovMax = DARMSInstanceGenerator.defUncovMax;
		int defUncovMin = DARMSInstanceGenerator.defUncovMin;
		int defUncovDiff = defUncovMax - defUncovMin;
		
		for(int i = 0; i < numDistributions; i++){
			Map<Flight, Integer> defCovMap = new HashMap<Flight, Integer>();
			Map<Flight, Integer> defUncovMap = new HashMap<Flight, Integer>();
			Map<Flight, Integer> attCovMap = new HashMap<Flight, Integer>();
			Map<Flight, Integer> attUncovMap = new HashMap<Flight, Integer>();
			
			for(Flight f : flights){
				int diff = (int)(((rand.nextDouble() - 0.5) * defUncovDiff * heterogeneity) + 0.5);
				
				int value = payoffStructure.defUncov(f) + diff;
				
				if(value > defUncovMax){
					value = defUncovMax;
				}
				
				if(value < defUncovMin){
					value = defUncovMin;
				}
				
				defCovMap.put(f, 0);
				defUncovMap.put(f, value);
				attCovMap.put(f, 0);
				attUncovMap.put(f, -value);
			}
						
			flightValueDistributionList.add(new PayoffStructure(defCovMap, defUncovMap, attCovMap, attUncovMap));
		}
		
		return flightValueDistributionList;
	}
}
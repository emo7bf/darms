package utilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

import models.DARMSModel;
import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;

public class DARMSPureStrategyGenerator{
	private DARMSModel model;
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> lowerBounds;
	
	public DARMSPureStrategyGenerator(DARMSModel model){
		this.model = model;
		this.lowerBounds = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
	
		for(int t : model.getTimeWindows()){
			lowerBounds.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>());
			
			for(Flight f : model.getFlights(t)){
				lowerBounds.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					lowerBounds.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						lowerBounds.get(t).get(f).get(c).put(o, 0);
					}
				}
			}
		}
	}
	
	public DARMSPureStrategyGenerator(DARMSModel model, Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> lowerBounds){
		this.model = model;
		this.lowerBounds = lowerBounds;
	}
	
	public List<PureStrategy> generateRandomizedPureStrategies(int numPureStrategies){
		List<PureStrategy> pureStrategies = new ArrayList<PureStrategy>();
		
		List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> screeningStrategies = generateRandomizedScreeningStrategies(numPureStrategies);
	
		for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> screeningStrategy : screeningStrategies){
			pureStrategies.add(new PureStrategy(screeningStrategy));
		}
	
		return pureStrategies;
	}
	
	public List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> generateRandomizedScreeningStrategies(int numScreeningStrategies){
		List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> pureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
		
		Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> unassignedPassengers = new HashMap<Integer, Map<Flight, Map<RiskCategory, Integer>>>();
		
		Map<Integer, Map<ScreeningResource, Integer>> unassignedResources = model.getScreeningResourceCapacities();
		
		Map<Integer, Set<ScreeningOperation>> remainingOperations = new HashMap<Integer, Set<ScreeningOperation>>();
		
		Random rand = new Random();
		
		for(int t : lowerBounds.keySet()){
			remainingOperations.put(t, new HashSet<ScreeningOperation>());
			
			for(ScreeningOperation o : model.getScreeningOperations()){
				remainingOperations.get(t).add(o);
			}
			
			for(Flight f : lowerBounds.get(t).keySet()){
				Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
				
				for(RiskCategory c : lowerBounds.get(t).get(f).keySet()){
					int unassigned = distribution.get(t).get(c);
					
					for(ScreeningOperation o : lowerBounds.get(t).get(f).get(c).keySet()){
						unassigned -= lowerBounds.get(t).get(f).get(c).get(o);
						
						for(ScreeningResource r : o.getResources()){
							unassignedResources.get(t).put(r, unassignedResources.get(t).get(r) - lowerBounds.get(t).get(f).get(c).get(o));
							
							if(unassignedResources.get(t).get(r) == 0){
								for(ScreeningOperation operation : model.getScreeningOperations()){
									if(operation.getResources().contains(r) && remainingOperations.get(t).contains(operation)){
										remainingOperations.get(t).remove(operation);
									}
								}
							}
						}
					}
					
					if(unassigned > 0){
						if(!unassignedPassengers.containsKey(t)){
							unassignedPassengers.put(t, new HashMap<Flight, Map<RiskCategory, Integer>>());
						}
						
						if(!unassignedPassengers.get(t).containsKey(f)){
							unassignedPassengers.get(t).put(f, new HashMap<RiskCategory, Integer>());
						}
						
						unassignedPassengers.get(t).get(f).put(c, unassigned);
					}
				}
			}
		}
		
		for(int i = 0; i < numScreeningStrategies; i++){
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy = copyPureStrategy(lowerBounds);
			
			Map<Integer, Map<ScreeningResource, Integer>> unassignedResourcesCopy = new HashMap<Integer, Map<ScreeningResource, Integer>>();
			
			for(int t : unassignedResources.keySet()){
				unassignedResourcesCopy.put(t, new HashMap<ScreeningResource, Integer>());
				
				for(ScreeningResource r : unassignedResources.get(t).keySet()){
					unassignedResourcesCopy.get(t).put(r, unassignedResources.get(t).get(r));
				}
			}
			
			Map<Integer, Map<ScreeningOperation, Integer>> unassignedOperations = new HashMap<Integer, Map<ScreeningOperation, Integer>>();
			
			for(int t : unassignedResourcesCopy.keySet()){
				unassignedOperations.put(t, new HashMap<ScreeningOperation, Integer>());
				
				for(ScreeningOperation o : remainingOperations.get(t)){
					unassignedOperations.get(t).put(o, Integer.MAX_VALUE);
					
					for(ScreeningResource r : o.getResources()){
						if(unassignedResources.get(t).get(r) < unassignedOperations.get(t).get(o)){
							unassignedOperations.get(t).put(o, unassignedResources.get(t).get(r));
						}
					}
				}
			}
			
			Map<Integer, Set<ScreeningOperation>> remainingOperationsCopy = new HashMap<Integer, Set<ScreeningOperation>>();
			
			for(int t : remainingOperations.keySet()){
				remainingOperationsCopy.put(t, new HashSet<ScreeningOperation>());
				
				for(ScreeningOperation o : remainingOperations.get(t)){
					remainingOperationsCopy.get(t).add(o);
				}
			}
			
			for(int t : unassignedPassengers.keySet()){
				for(Flight f : unassignedPassengers.get(t).keySet()){
					for(RiskCategory c : unassignedPassengers.get(t).get(f).keySet()){
						int unassigned = unassignedPassengers.get(t).get(f).get(c);
						
						for(int j = 0; j < unassigned; j++){
							ScreeningOperation assignedOperation = null;
							
							double prob = rand.nextDouble();
							
							int totalScreeningOperations = 0;
							
							for(ScreeningOperation o : unassignedOperations.get(t).keySet()){
								totalScreeningOperations += unassignedOperations.get(t).get(o);
							}
							
							for(ScreeningOperation o : remainingOperationsCopy.get(t)){
								prob -= 1.0 / (double)remainingOperationsCopy.get(t).size();
								//prob -= unassignedOperations.get(t).get(o) / (double)totalScreeningOperations;
								
								if(prob <= 0.0){
									assignedOperation = o;
									break;
								}
							}
							
							int assigned = pureStrategy.get(t).get(f).get(c).get(assignedOperation);
							
							pureStrategy.get(t).get(f).get(c).put(assignedOperation, assigned + 1);
							
							for(ScreeningResource r : assignedOperation.getResources()){
								unassignedResourcesCopy.get(t).put(r, unassignedResourcesCopy.get(t).get(r) - 1);
					
								if(unassignedResourcesCopy.get(t).get(r) == 0){
									for(ScreeningOperation o : model.getScreeningOperations()){
										if(o.getResources().contains(r) && remainingOperationsCopy.get(t).contains(o)){
											remainingOperationsCopy.get(t).remove(o);
										}
									}
								}
							}
							
							for(ScreeningOperation o : unassignedOperations.get(t).keySet()){
								for(ScreeningResource r : o.getResources()){
									if(unassignedResourcesCopy.get(t).get(r) < unassignedOperations.get(t).get(o)){
										unassignedOperations.get(t).put(o, unassignedResourcesCopy.get(t).get(r));
									}
								}
							}
							
						}
					}
				}
			}
			
			if(!pureStrategies.contains(pureStrategy)){
				pureStrategies.add(pureStrategy);
			}
			else{
				System.out.println("Duplicate Pure Strategy Generated.");
			}
		}
		
		return pureStrategies;
	}
	
	public List<PureStrategy> generatePureStrategies(){
		List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> pureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
		
		for(int t : model.getTimeWindows()){
			for(Flight f : model.getFlights(t)){
				Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> generatedPureStrategies = null;
					
					generatedPureStrategies = generatedPureStrategies(t, f, c, distribution.get(t).get(c));
					
					//System.out.println("T: " + t + " F: " + f + " C: " + c + " " + pureStrategies.size() + " " + generatedPureStrategies.size());
					
					if(pureStrategies.isEmpty()){
						pureStrategies.addAll(generatedPureStrategies);
					}
					else{
						List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> nextPureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
						
						for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> generatedPureStrategy : generatedPureStrategies){
							for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy : pureStrategies){
								Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategyCopy = copyPureStrategy(pureStrategy);
								
								if(!pureStrategyCopy.containsKey(t)){
									pureStrategyCopy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>());
								}
								
								if(!pureStrategyCopy.get(t).containsKey(f)){
									pureStrategyCopy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
								}
								
								if(!pureStrategyCopy.get(t).get(f).containsKey(c)){
									pureStrategyCopy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
								}
								
								for(ScreeningOperation o : model.getScreeningOperations()){
									pureStrategyCopy.get(t).get(f).get(c).put(o, generatedPureStrategy.get(t).get(f).get(c).get(o));
								}
								
								nextPureStrategies.add(pureStrategyCopy);
							}
						}
						
						pureStrategies = nextPureStrategies;
					}
				}
			}
		}
		
		List<PureStrategy> pureStrategyList = new ArrayList<PureStrategy>();
		
		//System.out.println("# Pure Strategies: " + pureStrategies.size());
		
		for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy : pureStrategies){
			
			if(validatePureStrategy(pureStrategy)){
				PureStrategy p = new PureStrategy(pureStrategy);
			
				pureStrategyList.add(p);
			}
		}
		
		//System.out.println("# Valid Pure Strategies: " + pureStrategyList.size());
		
		return pureStrategyList;
	}
	
	public boolean validatePureStrategy(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy){
		Map<Integer, Map<ScreeningResource, Integer>> unassignedScreeningResources = model.getScreeningResourceCapacities();
		
		for(int t : lowerBounds.keySet()){
			for(Flight f : model.getFlights(t)){
				Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					int assignedPassengers = distribution.get(t).get(c);
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						assignedPassengers -= pureStrategy.get(t).get(f).get(c).get(o);
						
						for(ScreeningResource r : o.getResources()){
							int unassignedResources = unassignedScreeningResources.get(t).get(r);
						
							unassignedResources -= pureStrategy.get(t).get(f).get(c).get(o);
							
							if(unassignedResources < 0){
								return false;
							}
							else{
								unassignedScreeningResources.get(t).put(r, unassignedResources);
							}
						}
					}
					
					if(assignedPassengers != 0){
						return false;
					}
				}
			}
		}
		
		return true;
	}
	
	private List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> generatedPureStrategies(Integer t, Flight f, RiskCategory c, int numPassengers){
		List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> pureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
		
		int unassignedPassengers = numPassengers;
		
		for(ScreeningOperation o :  model.getScreeningOperations()){
			unassignedPassengers -= lowerBounds.get(t).get(f).get(c).get(o);
		}
		
		for(ScreeningOperation o :  model.getScreeningOperations()){
			List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> generatedPureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
			
			for(int i = lowerBounds.get(t).get(f).get(c).get(o); i <= lowerBounds.get(t).get(f).get(c).get(o) + unassignedPassengers; i++){
				Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> generatedPureStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
				generatedPureStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>());
				generatedPureStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
				generatedPureStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
				generatedPureStrategy.get(t).get(f).get(c).put(o, i);
				
				generatedPureStrategies.add(generatedPureStrategy);
			}
			
			if(pureStrategies.isEmpty()){
				pureStrategies.addAll(generatedPureStrategies);
			}
			else{
				List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> nextPureStrategies = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
				
				for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> generatedPureStrategy : generatedPureStrategies){
					for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy : pureStrategies){
						Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategyCopy = copyPureStrategy(pureStrategy);
						
						pureStrategyCopy.get(t).get(f).get(c).put(o, generatedPureStrategy.get(t).get(f).get(c).get(o));
						
						int passengersAssigned = 0;
						
						for(ScreeningOperation n : pureStrategyCopy.get(t).get(f).get(c).keySet()){
							passengersAssigned += pureStrategyCopy.get(t).get(f).get(c).get(n);
						}
						
						if(passengersAssigned <= numPassengers){
							nextPureStrategies.add(pureStrategyCopy);
						}
					}
				}
				
				pureStrategies = nextPureStrategies;
			}
		}
		
		List<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>> pureStrategyList = new ArrayList<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>>();
		
		for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy : pureStrategies){
			int passengersAssigned = 0;
			
			for(ScreeningOperation o : pureStrategy.get(t).get(f).get(c).keySet()){
				passengersAssigned += pureStrategy.get(t).get(f).get(c).get(o);
			}
			
			if(passengersAssigned == numPassengers){
				pureStrategyList.add(pureStrategy);
			}
		}
		
		return pureStrategyList;
	}
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> copyPureStrategy(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategy){
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> pureStrategyCopy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		for(int t : pureStrategy.keySet()){
			pureStrategyCopy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>());
			
			for(Flight f : pureStrategy.get(t).keySet()){
				pureStrategyCopy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
				
				for(RiskCategory c : pureStrategy.get(t).get(f).keySet()){
					pureStrategyCopy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
					
					for(ScreeningOperation o :  pureStrategy.get(t).get(f).get(c).keySet()){
						pureStrategyCopy.get(t).get(f).get(c).put(o, pureStrategy.get(t).get(f).get(c).get(o));
					}
				}
			}
		}
		
		return pureStrategyCopy;
	}
}
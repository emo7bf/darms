package utilities;

import java.io.BufferedReader;
import java.io.FileReader;
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
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;

public class DARMSModelBuilder {
	public static boolean verb;
	
	public static DARMSModel buildModel(String inputFilename, boolean verbose, int thisTestNumber) throws Exception{
		verb = verbose;
		
		BufferedReader reader = new BufferedReader(new FileReader(inputFilename));
		
		String riskCategoryFilename = null;
		String flightListFilename = null;
		String screeningResourcesFilename = null;
		String screeningOperationsFilename = null;
		//String postScreeningResourcesFilename = null;
		
		Boolean flightByFlight = null;
		
		Integer shiftStartTime = null;
		Integer shiftDuration = null;
		Integer timeGranularity = null;
		String fineDist = null;
		Double fineMin = null;
		Double fineMax = null;
		Integer numberTests = null;
		
		List<AttackMethod> attackMethods = new ArrayList<AttackMethod>();
		
		String line = null;
		
		while((line = reader.readLine()) != null){
			if(line.startsWith("#")){
				continue;
			}
			
			String[] arg = line.split("=");
			
			if(arg.length != 2){
				continue;
			}
			
			arg[0] = arg[0].trim();
			arg[1] = arg[1].trim();

			if(arg[0].equalsIgnoreCase("RISK_CATEGORIES")){
				riskCategoryFilename = arg[1];
			}
			else if(arg[0].equalsIgnoreCase("FLIGHTS")){
				flightListFilename = arg[1];
			}
			else if(arg[0].equalsIgnoreCase("SCREENING_RESOURCES")){
				screeningResourcesFilename = arg[1];
			}
			else if(arg[0].equalsIgnoreCase("ATTACK_METHODS")){
				String[] list = arg[1].split(",");
				
				for(String s : list){
					attackMethods.add(new AttackMethod(s.trim()));
				}
			}
			else if(arg[0].equalsIgnoreCase("SCREENING_OPERATIONS")){
				screeningOperationsFilename = arg[1];
			}
			//else if(arg[0].equalsIgnoreCase("POST_SCREENING_RESOURCES")){
			//	postScreeningResourcesFilename = arg[1];
			//}
			else if(arg[0].equalsIgnoreCase("FLIGHT_BY_FLIGHT")){
				flightByFlight = Boolean.parseBoolean(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("SHIFT_START_TIME")){
				shiftStartTime = DARMSHelper.convertTimeToInteger(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("SHIFT_DURATION")){
				shiftDuration = Integer.parseInt(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("TIME_GRANULARITY")){
				timeGranularity = Integer.parseInt(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("TYPE_FINES")){
				fineDist = arg[1];
			}
			else if(arg[0].equalsIgnoreCase("FINE_MIN")){
				fineMin = Double.parseDouble(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("FINE_MAX")){
				fineMax = Double.parseDouble(arg[1]);
			}
			else if(arg[0].equalsIgnoreCase("NUMBER_TRIALS")){
				numberTests = Integer.parseInt(arg[1]);
			}
		}
		
		if(shiftDuration % timeGranularity > 0){
			throw new Exception("Shift Duration (" + shiftDuration + ") is not evenly divisible by Time Granularity (" + timeGranularity + ").");
		}
		
		Map<RiskCategory, Double> adversaryDistribution = getAdversaryDistribution(riskCategoryFilename);
		
		List<Flight> flightList = getFlights(adversaryDistribution.keySet(), flightListFilename);
		
		Map<ScreeningResource, Integer> screeningResources = getScreeningResources(adversaryDistribution.keySet(), attackMethods, screeningResourcesFilename);
		
		List<ScreeningOperation> screeningOperations = getScreeningOperations(screeningResources.keySet(), screeningOperationsFilename);
		
		//Map<PostScreeningResource, Integer> postScreeningResources = getPostScreeningResources(attackMethods, postScreeningResourcesFilename);
		Map<PostScreeningResource, Integer> postScreeningResources = new HashMap<PostScreeningResource, Integer>();

		DARMSModel model = new DARMSModel(flightList, 
				adversaryDistribution,
				attackMethods,
				screeningOperations,
				screeningResources, 
				postScreeningResources,
				flightByFlight,
				shiftStartTime,
				shiftDuration,
				timeGranularity,
				fineDist,
				fineMin,
				fineMax,
				numberTests,
				thisTestNumber
				);
		
		model.calculateTemporalPassengerDistributions();
		model.setPassengerDistribution();
		model.setPayoffStructure();
		
		return model;
	}
	
	private static Map<RiskCategory, Double> getAdversaryDistribution(String filename) throws Exception{
		Map<RiskCategory, Double> adversaryDistribution = new HashMap<RiskCategory, Double>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		String line = reader.readLine();
		
		while((line = reader.readLine()) != null){
			String[] arg = line.split(",");
			
			if(arg.length != 2){
				continue;
			}
			
			arg[0] = arg[0].trim();
			arg[1] = arg[1].trim();
			
			RiskCategory c = new RiskCategory(arg[0]);

			adversaryDistribution.put(c, Double.parseDouble(arg[1]));
		}
		
		double probability = 0.0;
		
		for(RiskCategory c : adversaryDistribution.keySet()){
			probability += adversaryDistribution.get(c);
		}
		
		if(Math.abs(1.0 - probability) > DARMSModel.EPSILON){
			throw new Exception("Adversary distribution is not a valid probability distribution.");
		}
		
		return adversaryDistribution;
	}
	
	private static List<Flight> getFlights(Set<RiskCategory> riskCategories, String filename) throws Exception{
		List<Flight> flightList = new ArrayList<Flight>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		Map<String, RiskCategory> riskCategoryMap = new HashMap<String, RiskCategory>();
		
		for(RiskCategory c : riskCategories){
			riskCategoryMap.put(c.toString().toLowerCase(), c);
		}
		
		String line = reader.readLine();
		
		String[] arg = line.split(",");
		
		Map<Integer, RiskCategory> columnMap = new HashMap<Integer, RiskCategory>();
		
		for(int i = 7; i < arg.length; i++){
			String c = arg[i].trim().toLowerCase();
			
			if(riskCategoryMap.containsKey(c)){
				columnMap.put(i, riskCategoryMap.get(c));
			}
			else{
				throw new Exception("Unrecognized risk category \"" +  arg[i].trim() + "\" in file " + filename + ".");
			}
		}
		
		for(RiskCategory c : riskCategories){
			if(!columnMap.values().contains(c)){
				throw new Exception("No passenger information found for risk category \"" +  c + "\" in file " + filename + ".");
			}
		}
		
		while((line = reader.readLine()) != null){
			arg = line.split(",");
			
			for(int i = 0; i < arg.length; i++){
				arg[i] = arg[i].trim();
			}
			
			String description = arg[0];
			
			Flight.FlightType flightType = null;
			
			if(arg[1].equalsIgnoreCase(Flight.FlightType.DOMESTIC.toString())){
				flightType = Flight.FlightType.DOMESTIC;
			}
			else if(arg[1].equalsIgnoreCase(Flight.FlightType.INTERNATIONAL.toString())){
				flightType = Flight.FlightType.INTERNATIONAL;
			}
			else{
				throw new Exception("Unsupported FlightType \"" +  arg[1] + "\" in file " + filename + ".");
			}
			
			int departureTime = DARMSHelper.convertTimeToInteger(arg[2]);
			
			int defUncovPayoff = Integer.parseInt(arg[3]);
			int defCovPayoff = Integer.parseInt(arg[4]);
			int attUncovPayoff = Integer.parseInt(arg[5]);
			int attCovPayoff = Integer.parseInt(arg[6]);
			
			Map<RiskCategory, Integer> distribution = new HashMap<RiskCategory, Integer>();
			
			for(int column : columnMap.keySet()){
				distribution.put(columnMap.get(column), Integer.parseInt(arg[column]));
			}
			
			Flight f = new Flight(description, flightType, departureTime, distribution);
			f.setPayoffs(defUncovPayoff, defCovPayoff, attUncovPayoff, attCovPayoff);
			
			flightList.add(f);
		}
				
		return flightList;
	}
	
	private static Map<ScreeningResource, Integer> getScreeningResources(Set<RiskCategory> riskCategories, List<AttackMethod> attackMethods, String filename) throws Exception{
		Map<ScreeningResource, Integer> screeningResources = new HashMap<ScreeningResource, Integer>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		String line = reader.readLine();
		
		String[] arg = line.split(",");
		
		while((line = reader.readLine()) != null){
			arg = line.split(",");
			
			String description = arg[0].trim();
			int quantity = Integer.parseInt(arg[1].trim());
			int capacity = Integer.parseInt(arg[2].trim());
			String effectivenessFilename = arg[3].trim();
			
			ScreeningResource r = new ScreeningResource(description, capacity, 0);
			
			Map<AttackMethod, Map<RiskCategory, Double>> resourceEffectiveness = getScreeningResourceEffectiveness(riskCategories, attackMethods, effectivenessFilename);

			r.setEffectiveness(resourceEffectiveness);
			
			screeningResources.put(r, quantity);
		}
		
		reader.close();
		
		return screeningResources;
	}
	
	private static Map<AttackMethod, Map<RiskCategory, Double>> getScreeningResourceEffectiveness(Set<RiskCategory> riskCategories, List<AttackMethod> attackMethods, String filename) throws Exception{
		Map<AttackMethod, Map<RiskCategory, Double>> effectivenessMap = new HashMap<AttackMethod, Map<RiskCategory, Double>>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		Map<String, RiskCategory> riskCategoryMap = new HashMap<String, RiskCategory>();
		
		for(RiskCategory c : riskCategories){
			riskCategoryMap.put(c.toString().toLowerCase(), c);
		}
		
		Map<String, AttackMethod> attackMethodMap = new HashMap<String, AttackMethod>();
		
		for(AttackMethod m : attackMethods){
			attackMethodMap.put(m.toString().toLowerCase(), m);
			effectivenessMap.put(m, null);
		}
		
		String line = reader.readLine();
		
		String[] arg = line.split(",");
		
		Map<Integer, RiskCategory> columnMap = new HashMap<Integer, RiskCategory>();
		
		for(int i = 1; i < arg.length; i++){
			String c = arg[i].trim().toLowerCase();
			
			if(riskCategoryMap.containsKey(c)){
				columnMap.put(i, riskCategoryMap.get(c));
			}
			else if(verb){
				System.out.println("IGNORED: Effectiveness information for risk category \"" + arg[i].trim() + "\" in file " + filename + ".");
			}
		}
		
		for(RiskCategory c : riskCategories){
			if(!columnMap.values().contains(c)){
				throw new Exception("No effectiveness information found for risk category \"" +  c + "\" in file " + filename + ".");
			}
		}
		
		while((line = reader.readLine()) != null){
			arg = line.split(",");
			
			String method = arg[0].trim().toLowerCase();
			
			if(attackMethodMap.containsKey(method)){
				AttackMethod m = attackMethodMap.get(method);
				
				effectivenessMap.put(m, new HashMap<RiskCategory, Double>());
				
				for(int i : columnMap.keySet()){
					effectivenessMap.get(m).put(columnMap.get(i), Double.parseDouble(arg[i].trim()));
				}
			}
			else if(verb){
				System.out.println("IGNORED: Effectiveness information for attack method \"" + arg[0].trim() + "\" in file " + filename + ".");
			}
		}
		
		for(AttackMethod m : effectivenessMap.keySet()){
			if(effectivenessMap.get(m) == null){
				throw new Exception("No effectiveness information found for attack method \"" +  m + "\" in file " + filename + ".");
			}
		}
		
		reader.close();
		
		return effectivenessMap;
	}
	
	public static List<ScreeningOperation> getScreeningOperations(Set<ScreeningResource> screeningResources, String filename) throws Exception{
		List<ScreeningOperation> screeningOperations = new ArrayList<ScreeningOperation>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		Map<String, ScreeningResource> screeningResourceMap = new HashMap<String, ScreeningResource>();
		
		for(ScreeningResource r : screeningResources){
			screeningResourceMap.put(r.toString().toLowerCase(), r);
		}
		
		String line = null;
		
		while((line = reader.readLine()) != null){
			String[] arg = line.split(",");
			
			boolean validOperation = true;
			
			Set<ScreeningResource> resources = new HashSet<ScreeningResource>();
			
			for(int i = 0; i < arg.length; i++){
				String resource = arg[i].trim().toLowerCase();
				
				if(!screeningResourceMap.containsKey(resource)){
					validOperation = false;
					
					System.out.println("IGNORED: Screening operation \"" + line + "\" due to missing sreening resource \"" + arg[i].trim() + "\".");
					
					break;
				}
				else{
					resources.add(screeningResourceMap.get(resource));
				}
			}
			
			if(validOperation){
				screeningOperations.add(new ScreeningOperation(resources));
				//TODO: check for duplicate security operations
			}
		}
		
		return screeningOperations;
	}
	
	private static Map<PostScreeningResource, Integer> getPostScreeningResources(List<AttackMethod> attackMethods, String filename) throws Exception{
		Map<PostScreeningResource, Integer> postScreeningResources = new HashMap<PostScreeningResource, Integer>();
		
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		if(verb){
			System.out.println("Reading in data file: " + filename);
		}
		
		Map<String, AttackMethod> attackMethodMap = new HashMap<String, AttackMethod>();
		
		for(AttackMethod m : attackMethods){
			attackMethodMap.put(m.toString().toLowerCase(), m);
		}
		
		String line = reader.readLine();
		
		String[] arg = line.split(",");
		
		Map<Integer, AttackMethod> columnMap = new HashMap<Integer, AttackMethod>();
		
		for(int i = 2; i < arg.length; i++){
			String m = arg[i].trim().toLowerCase();
			
			if(attackMethodMap.containsKey(m)){
				columnMap.put(i, attackMethodMap.get(m));
			}
			else if(verb){
				System.out.println("IGNORED: Effectiveness information for attack method \"" + arg[i].trim() + "\" in file " + filename + ".");
			}
		}
		
		for(AttackMethod m : attackMethods){
			if(!columnMap.values().contains(m)){
				throw new Exception("No effectiveness information found for attack method \"" +  m + "\" in file " + filename + ".");
			}
		}
		
		while((line = reader.readLine()) != null){
			arg = line.split(",");
			
			String description = arg[0].trim();
			int quantity = Integer.parseInt(arg[1].trim());
			
			Map<AttackMethod, Double> effectivenessMap = new HashMap<AttackMethod, Double>();
			
			for(int i : columnMap.keySet()){
				effectivenessMap.put(columnMap.get(i), Double.parseDouble(arg[i].trim()));
			}
			
			PostScreeningResource r = new PostScreeningResource(description, effectivenessMap);
		
			postScreeningResources.put(r, quantity);
		}
		
		reader.close();
		
		return postScreeningResources;
	}
}
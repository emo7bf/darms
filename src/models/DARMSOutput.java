package models;

import java.io.BufferedReader;
import java.io.FileReader;

public class DARMSOutput {
	String defenderScreeningStrategyFile;
	String defenderPostScreeningStrategyFile;
	String adversaryStrategiesFile;
	String adversaryPayoffsFile;
	String defenderPayoffsFile;
	String flightRiskCategoryCoverageFile;
	String passengerDistributionFile;
	String resourceFinesFile;

	public DARMSOutput(String filename){
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			
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
	
				if(arg[0].equalsIgnoreCase("SCREENING_STRATEGY_FILE")){
					defenderScreeningStrategyFile = arg[1];
				}
				//else if(arg[0].equalsIgnoreCase("POST_SCREENING_STRATEGY_FILE")){
				//	defenderPostScreeningStrategyFile = arg[1];
				//}
				else if(arg[0].equalsIgnoreCase("ADVERSARY_STRATEGIES_FILE")){
					adversaryStrategiesFile = arg[1];
				}
				else if(arg[0].equalsIgnoreCase("ADVERSARY_PAYOFFS_FILE")){
					adversaryPayoffsFile = arg[1];
				}
				else if(arg[0].equalsIgnoreCase("DEFENDER_PAYOFFS_FILE")){
					defenderPayoffsFile = arg[1];
				}
				else if(arg[0].equalsIgnoreCase("FLIGHT_RISK_CATEGORY_COVERAGE_FILE")){
					flightRiskCategoryCoverageFile = arg[1];
				}
				else if(arg[0].equalsIgnoreCase("PASSENGER_DISTRIBUTION_FILE")){
					passengerDistributionFile = arg[1];
				}
				else if(arg[0].equalsIgnoreCase("RESOURCE_FINES_FILE")){
					resourceFinesFile = arg[1];
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public String defenderScreeningStrategyFile(){
		return defenderScreeningStrategyFile;
	}
	
	public String defenderPostScreeningStrategyFile(){
		return defenderPostScreeningStrategyFile;
	}
	
	public String adversaryStrategiesFile(){
		return adversaryStrategiesFile;
	}
	
	public String adversaryPayoffsFile(){
		return adversaryPayoffsFile;
	}
	
	public String defenderPayoffsFile(){
		return defenderPayoffsFile;
	}
	
	public String flightRiskCategoryCoverageFile(){
		return flightRiskCategoryCoverageFile;
	}
	
	public String passengerDistributionFile(){
		return passengerDistributionFile;
	}
	
	public String resourceFinesFile(){
		return resourceFinesFile;
	}
}
package utilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import models.AdversaryDistribution;
import models.Flight;
import models.PayoffStructure;
import models.RiskCategory;
import models.ScreeningOperation;

public class DARMSHelper {
	public static boolean loadLibrariesCplex(String ConfigFile) throws IOException{
		FileReader fstream = new FileReader(ConfigFile);
		BufferedReader in = new BufferedReader(fstream);

		String CplexFileString = null;

		String line = in.readLine();
		
		while(line != null){
			line.trim();
			
			if(line.length() > 0 && !line.startsWith("#")){
				String[] list = line.split("=");
				
				if(list.length != 2){
					throw new RuntimeException("Unrecognized format for the config file.\n");
				}
				
				if(list[0].trim().equalsIgnoreCase("LIB_FILE")){
					CplexFileString = list[1];
				}
				else{
					System.err.println("Unrecognized statement in Config File: " + line);
					return false;
				}
			}
			
			line = in.readLine();
		}

		File CplexFile = new File(CplexFileString);
		
		System.load(CplexFile.getAbsolutePath());
		
		return true;
	}
	
	public static int convertTimeToInteger(String time) throws Exception{
		String[] arg = time.split(":");
		
		if(arg.length != 2){
			throw new Exception("Improperly formatted time \"" +  time + ".");
		}
		
		int hour = Integer.parseInt(arg[0]);
		int minute = Integer.parseInt(arg[1]);
		
		if(hour < 0 || hour > 23){
			throw new Exception("Improperly formatted time \"" +  time + ".");
		}
		
		if(minute < 0 || minute > 59){
			throw new Exception("Improperly formatted time \"" +  time + ".");
		}
		
		return (hour * 60) + minute;
	}
	
	public static String convertIntegerToTime(int integer){
		int hour = integer / 60;
		int minute = integer % 60;
		
		String sHour = String.valueOf(hour);
		String sMinute = String.valueOf(minute); 
		
		if(hour < 10){
			sHour = "0" + sHour;
		}
		
		if(minute < 10){
			sMinute = "0" + sMinute;
		}
		
		return sHour + ":" + sMinute;
	}
	
	public static Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> getFlooredScreeningStrategy(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginal){
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> lowerBoundConstraints = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		for(int t : marginal.keySet()){
			lowerBoundConstraints.put(t, new HashMap<Flight, Map<RiskCategory,Map<ScreeningOperation, Integer>>>());
			
			for(Flight f : marginal.get(t).keySet()){
				lowerBoundConstraints.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
				
				for(RiskCategory c : marginal.get(t).get(f).keySet()){
					lowerBoundConstraints.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
					
					for(ScreeningOperation o : marginal.get(t).get(f).get(c).keySet()){
						int lowerBound = (int)(marginal.get(t).get(f).get(c).get(o).doubleValue());
						
						lowerBoundConstraints.get(t).get(f).get(c).put(o, lowerBound);
					}
				}
			}
		}
		
		return lowerBoundConstraints;
	}
	
	public static double calculateSquaredDistance(AdversaryDistribution ad1, AdversaryDistribution ad2){
		double totalSquaredDiff = 0.0;
		
		for(RiskCategory c : ad1.keySet()){
			totalSquaredDiff += Math.pow(ad1.get(c) - ad2.get(c), 2.0);
		}
		
		return Math.pow(totalSquaredDiff, 0.5);
	}
	
	public static double calculateSquaredDistance(PayoffStructure ps1, PayoffStructure ps2){
		double totalSquaredDiff = 0.0;
		
		for(Flight f : ps1.keySet()){
			totalSquaredDiff += Math.pow(ps1.defUncov(f) - ps2.defUncov(f), 2.0);
		}
		
		return Math.pow(totalSquaredDiff, 0.5);
	}
}
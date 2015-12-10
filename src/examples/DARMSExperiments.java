package examples;
import java.util.ArrayList;
import java.util.List;

import utilities.DARMSInstanceGenerator;


import models.AttackMethod;
import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;


public class DARMSExperiments{
	public static void main(String[] args){
		try{
			String directory = args[0];
			int numFlights = Integer.parseInt(args[1]);
			int numRiskCategories = Integer.parseInt(args[2]);
			boolean decomposed = Boolean.parseBoolean(args[3]);
			
			if(numRiskCategories != 4 && numRiskCategories != 6){
				throw new Exception("Unsupported number of passenger risk categories: " + numRiskCategories);
			}
			
			long seed = DARMSInstanceGenerator.generateUniformInstance(directory, 1, numFlights, numRiskCategories);
				
			String[] decomposedExperiment = {directory + "/CplexConfig", 
					directory + "/InputDARMS." + seed + ".txt", 
					directory + "/OutputDARMS." + seed + ".txt", 
					"true",
					"false"};
				
			String[] nonDecomposedExperiment = {directory + "/CplexConfig", 
					directory + "/InputDARMS." + seed + ".txt", 
					directory + "/OutputDARMS." + seed + ".txt", 
					"false",
					"false"};
				
			List<String[]> argList = new ArrayList<String[]>();
				
			argList.add(decomposedExperiment);
			argList.add(nonDecomposedExperiment);
				
			for(String[] arg : argList){
				ExampleDARMS.main(arg);
				Flight.reset();
				RiskCategory.reset();
				AttackMethod.reset();
				ScreeningResource.reset();
				ScreeningOperation.reset();
				PureStrategy.reset();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}
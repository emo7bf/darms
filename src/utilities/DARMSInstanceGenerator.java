package utilities;
import java.io.File;
import java.io.FileWriter;
import java.util.Random;

public class DARMSInstanceGenerator {
	public static int defCovMin = 0;
	public static int defCovMax = 0;
	public static int defUncovMin = -10;
	public static int defUncovMax = -1;
	
	public static int SelecteeMax = 10;
	public static int SelecteeMin = 1;
	public static int UnknownMax = 150;
	public static int UnknownMin = 50;
	public static int LowRisk1Max = 50;
	public static int LowRisk1Min = 25;
	public static int LowRisk2Max = 25;
	public static int LowRisk2Min = 5;
	public static int LowRisk3Max = 10;
	public static int LowRisk3Min = 1;
	public static int LowRisk4Max = 10;
	public static int LowRisk4Min = 1;
	
	public static int shiftStartTime = 240;
	public static int shiftDuration = 60;
	public static int timeGranularity = 60;
	
	public static void main(String[] args){
		try{
			String directory = args[0];
			int numInstances = Integer.parseInt(args[1]);
			int numFlights = Integer.parseInt(args[2]);
			int numRiskCategories = Integer.parseInt(args[3]);
			boolean normal = Boolean.parseBoolean(args[4]);
			
			if(numRiskCategories != 4 && numRiskCategories != 6){
				throw new Exception("Unsupported number of passenger risk categories: " + numRiskCategories);
			}
			
			for(int index = 1; index <= numInstances; index++){	
				if(normal){
					double heterogeneity = Double.parseDouble(args[5]);
						
					generateNormalInstance(directory, index, numFlights, numRiskCategories, heterogeneity);
				}
				else{
					generateUniformInstance(directory, index, numFlights, numRiskCategories);
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static long generateUniformInstance(String directory, int index, int numFlights, int numRiskCategories) throws Exception{
		long seed = System.currentTimeMillis();
		
		generateUniformFlightFile(directory, seed, numFlights, numRiskCategories);
		generateInputFile(directory, seed, index, numFlights, numRiskCategories, true);
		//generateInputFile(directory, seed, numRiskCategories, false);
		//generateOutputFile(directory, seed, true);
		//generateOutputFile(directory, seed, false);
		
		return seed;
	}
	
	public static long generateNormalInstance(String directory, int index, int numFlights, int numRiskCategories, double heterogeneity) throws Exception{
		long seed = System.currentTimeMillis();
		
		generateNormalFlightFile(directory, seed, numFlights, heterogeneity);
		generateInputFile(directory, seed, index, numFlights, numRiskCategories, true);
		//generateInputFile(directory, seed, numRiskCategories, false);
		//generateOutputFile(directory, seed, true);
		//generateOutputFile(directory, seed, false);
		
		return seed;
	}
	
	public static void generateUniformFlightFile(String directory, long seed, int numFlights, int numRiskCategories) throws Exception{
		FileWriter fw = new FileWriter(new File(directory + "/input/Flights" + seed + ".csv"));
		
		int defCovDiff = defCovMax - defCovMin;
		int defUncovDiff = defUncovMax - defUncovMin;
		
		int SelecteeDiff = SelecteeMax - SelecteeMin;
		int UnknownDiff = UnknownMax - UnknownMin;
		int LowRisk1Diff = LowRisk1Max - LowRisk1Min;
		int LowRisk2Diff = LowRisk2Max - LowRisk2Min;
		int LowRisk3Diff = LowRisk3Max - LowRisk3Min;
		int LowRisk4Diff = LowRisk4Max - LowRisk4Min;
		
		Random r = new Random(seed);
		
		fw.write("Description, FlightType, DepartureTime, DefUncov, DefCov, AttUncov, AttCov, ");
		
		if(numRiskCategories == 4){
			fw.write("SELECTEE, UNKNOWN, LOWRISK1, LOWRISK2\n");
		}
		else{
			fw.write("SELECTEE, UNKNOWN, LOWRISK1, LOWRISK2, LOWRISK3, LOWRISK4\n");
		}
		
		for(int i = 1; i <= numFlights; i++){
			String description = "Flight" + i;
			
			String flightType = "DOMESTIC";
			
			int departureTime = shiftStartTime + (int)((r.nextDouble() * shiftDuration) + 0.5);
			
			int defCov = defCovMin + (int)((r.nextDouble() * defCovDiff) + 0.5);
			int attCov = -defCov;
			
			int defUncov = defUncovMin + (int)((r.nextDouble() * defUncovDiff) + 0.5);
			int attUncov = -defUncov;
			
			int numSelectee = SelecteeMin + (int)(r.nextDouble() * SelecteeDiff);
			int numUnknown = UnknownMin + (int)(r.nextDouble() * UnknownDiff);
			int numLowRisk1 = LowRisk1Min + (int)(r.nextDouble() * LowRisk1Diff);
			int numLowRisk2 = LowRisk2Min + (int)(r.nextDouble() * LowRisk2Diff);
			int numLowRisk3 = LowRisk3Min + (int)(r.nextDouble() * LowRisk3Diff);
			int numLowRisk4 = LowRisk4Min + (int)(r.nextDouble() * LowRisk4Diff);
			
			String line = description + ", " + flightType + ", " + DARMSHelper.convertIntegerToTime(departureTime) + ", ";
			line += defUncov + ", " + defCov + ", " + attUncov + ", " + attCov + ", ";
			
			line += numSelectee + ", " + numUnknown + ", " + numLowRisk1 + ", " + numLowRisk2;
			
			if(numRiskCategories == 6){
				line += ", " + numLowRisk3 + ", " + numLowRisk4;
			}
			
			if(i < numFlights){
				line += "\n";
			}
			
			fw.write(line);
		}
		
		fw.close();
	}
	
	public static void generateNormalFlightFile(String directory, long seed, int numFlights, double heterogeneity) throws Exception{
		FileWriter fw = new FileWriter(new File(directory + "/input/Flights" + seed + ".csv"));
		
		int defCovDiff = defCovMax - defCovMin;
		int defUncovDiff = defUncovMax - defUncovMin;
		
		double defCovMid = (defCovMax + defCovMin) / 2.0;
		double defUncovMid = (defUncovMax + defUncovMin) / 2.0;
		
		int SelecteeDiff = SelecteeMax - SelecteeMin;
		int UnknownDiff = UnknownMax - UnknownMin;
		int LowRisk1Diff = LowRisk1Max - LowRisk1Min;
		int LowRisk2Diff = LowRisk2Max - LowRisk2Min;
		int LowRisk3Diff = LowRisk3Max - LowRisk3Min;
		int LowRisk4Diff = LowRisk4Max - LowRisk4Min;
		
		double SelecteeMid = (SelecteeMax + SelecteeMin) / 2.0;
		double UnknownMid = (UnknownMax + UnknownMin) / 2.0;
		double LowRisk1Mid = (LowRisk1Max + LowRisk1Min) /2.0;
		double LowRisk2Mid = (LowRisk2Max + LowRisk2Min) / 2.0;
		double LowRisk3Mid = (LowRisk3Max + LowRisk3Min) /2.0;
		double LowRisk4Mid = (LowRisk4Max + LowRisk4Min) / 2.0;
		
		Random r = new Random(seed);
		
		fw.write("Description, DefUncov, DefCov, AttUncov, AttCov, ");
		fw.write("SELECTEE, UNKNOWN, LOWRISK1, LOWRISK2\n");
		
		for(int i = 1; i <= numFlights; i++){
			String description = "Flight" + i;
			int defCov = (int)(defCovMid + (r.nextGaussian() * defCovDiff * 0.25 * heterogeneity) + 0.5);
			
			if(defCov < defCovMin){
				defCov = defCovMin;
				//System.out.println("Flight" + i + " violated lower bound on defCov.");
			}
			else if (defCov > defCovMax){
				defCov = defCovMax;
				//System.out.println("Flight" + i + " violated upper bound on defCov.");
			}
			
			int attCov = -defCov;
			
			int defUncov = (int)(defUncovMid + (r.nextGaussian() * defUncovDiff * 0.25 * heterogeneity) + 0.5);
			
			if(defUncov < defUncovMin){
				defUncov = defUncovMin;
				//System.out.println("Flight" + i + " violated lower bound on defUncov.");
			}
			else if(defUncov > defUncovMax){
				defUncov = defUncovMax;
				//System.out.println("Flight" + i + " violated upper bound on defUncov.");
			}
			
			int attUncov = -defUncov;
			
			int numSelectee = (int)(SelecteeMid + (r.nextGaussian() * SelecteeDiff * 0.25 * heterogeneity) + 0.5);
			
			if(numSelectee < SelecteeMin){
				numSelectee = SelecteeMin;
				//System.out.println("Flight" + i + " violated lower bound on numSelectee.");
			}
			else if(numSelectee > SelecteeMax){
				numSelectee = SelecteeMax;
				//System.out.println("Flight" + i + " violated upper bound on numSelectee.");
			}
			
			int numUnknown = (int)(UnknownMid + (r.nextGaussian() * UnknownDiff * 0.25 * heterogeneity) + 0.5);
			
			if(numUnknown < UnknownMin){
				numUnknown = UnknownMin;
				//System.out.println("Flight" + i + " violated lower bound on numUnknown.");
			}
			else if(numUnknown > UnknownMax){
				numUnknown = UnknownMax;
				//System.out.println("Flight" + i + " violated upper bound on numUnknown.");
			}
			
			int numLowRisk1 = (int)(LowRisk1Mid + (r.nextGaussian() * LowRisk1Diff * 0.25 * heterogeneity) + 0.5);
			
			if(numLowRisk1 < LowRisk1Min){
				numLowRisk1 = LowRisk1Min;
				//System.out.println("Flight" + i + " violated lower bound on numLowRisk1.");
			}
			else if(numLowRisk1 > LowRisk1Max){
				numLowRisk1 = LowRisk1Max;
				//System.out.println("Flight" + i + " violated upper bound on numLowRisk1.");
			}
			
			int numLowRisk2 = (int)(LowRisk2Mid + (r.nextGaussian() * LowRisk2Diff * 0.25 * heterogeneity) + 0.5);
			
			if(numLowRisk2 < LowRisk2Min){
				numLowRisk2 = LowRisk2Min;
				//System.out.println("Flight" + i + " violated lower bound on numLowRisk2.");
			}
			else if(numLowRisk2 > LowRisk2Max){
				numLowRisk2 = LowRisk2Max;
				//System.out.println("Flight" + i + " violated upper bound on numLowRisk2.");
			}
			
			
			/////
			int numLowRisk3 = (int)(LowRisk3Mid + (r.nextGaussian() * LowRisk3Diff * 0.25 * heterogeneity) + 0.5);
			
			if(numLowRisk3 < LowRisk3Min){
				numLowRisk3 = LowRisk3Min;
			}
			else if(numLowRisk3 > LowRisk3Max){
				numLowRisk3 = LowRisk3Max;
			}
			
			int numLowRisk4 = (int)(LowRisk4Mid + (r.nextGaussian() * LowRisk4Diff * 0.25 * heterogeneity) + 0.5);
			
			if(numLowRisk4 < LowRisk4Min){
				numLowRisk4 = LowRisk4Min;
			}
			else if(numLowRisk4 > LowRisk4Max){
				numLowRisk4 = LowRisk4Max;
			}
			
			fw.write(description + ", " + defUncov + ", " + defCov + ", " + attUncov + ", " + attCov + ", ");
			
			if(i == numFlights){
				fw.write(numSelectee + ", " + numUnknown + ", " + numLowRisk1 + ", " + numLowRisk2 + ", " + numLowRisk3 + ", " + numLowRisk4);
			}
			else{
				fw.write(numSelectee + ", " + numUnknown + ", " + numLowRisk1 + ", " + numLowRisk2 + ", " + numLowRisk3 + ", " + numLowRisk4 + "\n");
			}
		}
		
		fw.close();
	}
	
	public static void generateInputFile(String directory, long seed, int index, int numFlights, int numRiskCategories, boolean flightByFlight) throws Exception{
		FileWriter fw = new FileWriter(new File(directory + "/InputDARMS." + numFlights + "." + index + "." + flightByFlight + ".txt"));
		
		if(numRiskCategories == 4){
			fw.write("RISK_CATEGORIES = " + directory + "/input/RiskCategories.4.csv\n");
		}
		else if(numRiskCategories == 6){
			fw.write("RISK_CATEGORIES = " + directory + "/input/RiskCategories.6.csv\n");
		}
		
		fw.write("FLIGHTS = " + directory + "/input/Flights" + seed + ".csv\n");
		fw.write("SCREENING_RESOURCES = " + directory + "/input/ScreeningResources.csv\n");
		fw.write("SCREENING_OPERATIONS = " + directory + "/input/ScreeningOperations.csv\n");
		fw.write("POST_SCREENING_RESOURCES = " + directory + "/input/PostScreeningResources.Null.csv\n");
		
		fw.write("FLIGHT_BY_FLIGHT = " + flightByFlight + "\n");
		fw.write("ATTACK_METHODS = attOnBody, attInCarryOn\n");
		fw.write("SHIFT_START_TIME = " + DARMSHelper.convertIntegerToTime(shiftStartTime) + "\n");
		fw.write("SHIFT_DURATION = " + shiftDuration + "\n");
		fw.write("TIME_GRANULARITY = " + timeGranularity);
		
		fw.close();
	}
	
	public static void generateOutputFile(String directory, long seed, boolean flightByFlight) throws Exception{
		FileWriter fw = new FileWriter(new File(directory + "/OutputDARMS." + seed + "." + flightByFlight + ".txt"));
		
		fw.write("SCREENING_STRATEGY_FILE = " + directory + "/output/ScreeningStrategy." + seed + "." + flightByFlight + ".csv\n");
		fw.write("POST_SCREENING_STRATEGY_FILE = " + directory + "/output/PostScreeningStrategy." + seed + "." + flightByFlight + ".csv\n");
		fw.write("ADVERSARY_STRATEGIES_FILE = " + directory + "/output/AdversaryStrategies." + seed + "." + flightByFlight + ".csv\n");
		fw.write("ADVERSARY_PAYOFFS_FILE = " + directory + "/output/AdversaryPayoffs." + seed + "." + flightByFlight + ".csv\n");
		fw.write("DEFENDER_PAYOFFS_FILE = " + directory + "/output/DefenderPayoffs." + seed + "." + flightByFlight + ".csv\n");
		fw.write("FLIGHT_RISK_CATEGORY_COVERAGE_FILE = " + directory + "/output/FlightRiskCategoryCoverage." + seed + "." + flightByFlight + ".csv\n");
		fw.write("PASSENGER_DISTRIBUTION_FILE = " + directory + "/output/PassengerDistribution." + seed + "." + flightByFlight + ".csv");
		
		fw.close();
	}
}
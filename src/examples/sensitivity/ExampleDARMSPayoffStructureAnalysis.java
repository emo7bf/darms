package examples.sensitivity;

import java.util.List;
import java.util.Map;

import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

import models.DARMSModel;
import models.Flight;
import models.PayoffStructure;
import models.RiskCategory;
import models.ScreeningOperation;

public class ExampleDARMSPayoffStructureAnalysis{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			int numSamples = Integer.parseInt(args[2]);
			double heterogeneity = Double.parseDouble(args[3]);
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			PayoffStructure defaultStructure = model.getPayoffStructure();
			
			DARMSMarginalSolver defaultSolver = new DARMSMarginalSolver(model, true, false, true, false);
			
			defaultSolver.solve();
			
			//double defaultDefenderPayoff = defaultSolver.getDefenderPayoff();
			
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> screeningStrategy = defaultSolver.getDefenderScreeningStrategy();
			
			List<PayoffStructure> payoffStructureList = model.getRandomizedPayoffStructures(numSamples, heterogeneity);
			
			for(PayoffStructure ps : payoffStructureList){
				DARMSMarginalSolver solver = new DARMSMarginalSolver(model, ps, true, false, true, false);
				
				solver.solve();
				
				double defenderPayoff = solver.calculateDefenderPayoff(screeningStrategy);
				
				double absoluteRegret = solver.getDefenderPayoff() - defenderPayoff;
				
				double distance = DARMSHelper.calculateSquaredDistance(defaultStructure, ps);
				
				double relativeRegret = Math.abs(absoluteRegret / defenderPayoff);
				
				System.out.println(inputFile + " " + ps + " " + heterogeneity + " " + distance + " " + " " + absoluteRegret + " " + relativeRegret);
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}
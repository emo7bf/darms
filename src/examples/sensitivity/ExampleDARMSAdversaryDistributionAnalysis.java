package examples.sensitivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

import models.AdversaryDistribution;
import models.DARMSModel;
import models.RiskCategory;

public class ExampleDARMSAdversaryDistributionAnalysis{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			int numSamples = Integer.parseInt(args[2]);
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			AdversaryDistribution defaultDistribution = new AdversaryDistribution(model.getAdversaryDistribution());
			
			DARMSMarginalSolver defaultSolver = new DARMSMarginalSolver(model, true, false, true, false);
			
			defaultSolver.solve();
			
			List<AdversaryDistribution> adversaryDistributionList = model.getRandomizedAdversaryDistributions(numSamples);
			
			Map<RiskCategory, Double> defenderPayoffs = defaultSolver.getDefenderPayoffs();
			
			List<RiskCategory> categoryList = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
			
			Collections.sort(categoryList);
			
			for(AdversaryDistribution ad : adversaryDistributionList){
				model.setAdversaryDistribution(ad.distribution());
				
				DARMSMarginalSolver solver = new DARMSMarginalSolver(model, true, false, true, false);
				
				solver.solve();
				
				double defenderPayoff = 0.0;
				
				for(RiskCategory c : categoryList){
					defenderPayoff += ad.get(c) * defenderPayoffs.get(c);
				}
				
				double absoluteRegret = solver.getDefenderPayoff() - defenderPayoff;
				
				double relativeRegret = Math.abs(absoluteRegret / defenderPayoff); 
				
				double distance = DARMSHelper.calculateSquaredDistance(defaultDistribution, ad);
				
				double roundedDistance = ((int)(distance * 10) + 1) / 10.0;
				
				System.out.print(inputFile + " " + ad + " " + distance + " " + roundedDistance + " " + absoluteRegret + " " + relativeRegret);
			
				for(RiskCategory c : categoryList){
					System.out.print(" " + ad.get(c));
				}
				
				System.out.println();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}
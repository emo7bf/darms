package examples;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class ExperimentParser {
	
	public static void main(String[] args) throws Exception{
		
		for(int flights = 25; flights <= 100; flights+= 25){
			for(int index = 1; index <= 30; index++){
				for(int iterations = 50; iterations <= 200; iterations += 50){
					//System.out.println("qsub -S /bin/bash -k n -N " + flights + "_" + index + "_" + iterations + " -l nodes=1:ppn=10,walltime=24:00:00 -z -v numFlights='" + flights + "',warmStartIterations='" + iterations + "',betterResponse='false',index='" + index + "' /auto/rcf-40/mattheab/DARMS/DARMS_optimal.sh");
					//System.out.println("qsub -S /bin/bash -k n -N " + flights + "_" + index + "_" + iterations + " -l nodes=1:ppn=10,walltime=24:00:00 -z -v numFlights='" + flights + "',warmStartIterations='" + iterations + "',betterResponse='true',index='" + index + "' /auto/rcf-40/mattheab/DARMS/DARMS_optimal.sh");
				}
			}
		}
		
		
		String directory = "C:\\Users\\Matthew\\Desktop\\cutoff_updated\\";
		
		File folder = new File(directory);
		File[] listOfFiles = folder.listFiles(); 
		
		for(int i = 0; i < listOfFiles.length; i++){  
			if(listOfFiles[i].isFile()){
				String file = listOfFiles[i].getName();
				
				if(file.startsWith("output") && file.endsWith(".txt")){
					BufferedReader fileReader = new BufferedReader(new FileReader(directory + file));
					
					String previousLine = null;
					String currentLine = null;
					
					int failedBetterResponses = 0;
					
					while((currentLine = fileReader.readLine()) != null){
						previousLine = currentLine;
						
						if(currentLine.contains("Better Response Failed")){
							//System.out.println("Failed Better Response");
							failedBetterResponses++;
							
						}
					}
					
					System.out.println(previousLine);
					//System.out.println(previousLine + " " + failedBetterResponses);
		        }
			}
		}
	}
}
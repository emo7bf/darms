package utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import models.DARMSModel;
import models.Flight;
import models.PureStrategy;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;

import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import solvers.DARMSMarginalSolver;

public class DARMSPureStrategySampler {
	DARMSModel model;
	Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginalStrategy;
	
	SimpleDirectedWeightedGraph<Vertex, Edge> graph;
	Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Edge>>> edgeMap;
	Map<ScreeningOperation, Vertex> operationVertexMap;
	Map<ScreeningResource, Vertex> resourceVertexMap;
	Map<Vertex, ScreeningOperation>vertexOperationMap;
	Map<Vertex, ScreeningResource> vertexResourceMap;
	Map<ScreeningResource, Integer> totalResourceCapacityMap;
	Map<ScreeningResource, Double> remainingResourceCapacityMap;
	Map<ScreeningOperation, Double> remainingOperationCapacityMap;
	
	Random random;
	
	public DARMSPureStrategySampler(DARMSModel model, DARMSMarginalSolver solver){
		this.model = model;
		this.marginalStrategy = solver.getDefenderMarginalScreeningStrategy();
		
		this.random = new Random();
		
		constructPointerMap();
	}
	
	public DARMSPureStrategySampler(DARMSModel model, Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginalStrategy){
		this.model = model;
		this.marginalStrategy = marginalStrategy;
		
		this.random = new Random();
		
		constructPointerMap();
	}
	
	public void constructPointerMap(){
		Map<ScreeningResource, Set<ScreeningOperation>> resourceOperationMap = new HashMap<ScreeningResource, Set<ScreeningOperation>>();
		
		for(ScreeningResource r : model.getScreeningResources().keySet()){
			Set<ScreeningOperation> operationSet = new HashSet<ScreeningOperation>();
			
			for(ScreeningOperation o : model.getScreeningOperations()){
				if(o.getResources().contains(r)){
					operationSet.add(o);
				}
			}
			
			resourceOperationMap.put(r, operationSet);
		}
		
		Map<ScreeningResource, Set<ScreeningResource>> resourceSubsetMap = new HashMap<ScreeningResource, Set<ScreeningResource>>();
		
		Map<ScreeningResource, Map<ScreeningResource, Set<ScreeningOperation>>> resourceIntersectionMap = new HashMap<ScreeningResource, Map<ScreeningResource, Set<ScreeningOperation>>>();
		
		for(ScreeningResource r1 : resourceOperationMap.keySet()){
			resourceIntersectionMap.put(r1, new HashMap<ScreeningResource, Set<ScreeningOperation>>());
			
			for(ScreeningResource r2 : resourceOperationMap.keySet()){
				if(r1 != r2){
					Set<ScreeningOperation> s1 = resourceOperationMap.get(r1);
					Set<ScreeningOperation> s2 = resourceOperationMap.get(r2);
						
					if(s1.containsAll(s2)){
						if(!resourceSubsetMap.containsKey(r1)){
							resourceSubsetMap.put(r1, new HashSet<ScreeningResource>());
						}
						
						resourceSubsetMap.get(r1).add(r2);
					}
					else if(s2.containsAll(s1)){
						if(!resourceSubsetMap.containsKey(r2)){
							resourceSubsetMap.put(r2, new HashSet<ScreeningResource>());
						}
						
						resourceSubsetMap.get(r2).add(r1);
					}
					else{	
						Set<ScreeningOperation> intersectionSet = new HashSet<ScreeningOperation>();
						
						for(ScreeningOperation o : s1){
							if(s2.contains(o)){
								intersectionSet.add(o);
							}
						}
						
						resourceIntersectionMap.get(r1).put(r2, intersectionSet);
					}
				}
			}
		}
		
		Map<String, ScreeningResource> stringMap = new HashMap<String, ScreeningResource>();
		
		for(ScreeningResource r : resourceIntersectionMap.keySet()){
			stringMap.put(r.toString(), r);
		}
		
		
		List<ScreeningResource> resourceIntersectionList = new ArrayList<ScreeningResource>();
		
		resourceIntersectionList.add(stringMap.get("WTMD"));
		resourceIntersectionList.add(stringMap.get("AIT"));
		resourceIntersectionList.add(stringMap.get("PATDOWN"));
		resourceIntersectionList.add(stringMap.get("ETD"));
		
		Set<ScreeningResource> tempSet1 = new HashSet<ScreeningResource>();
		tempSet1.add(stringMap.get("PATDOWN"));
		
		//resourceSubsetMap.put(stringMap.get("AIT"), tempSet1);
		
		Set<ScreeningResource> tempSet2 = new HashSet<ScreeningResource>();
		tempSet2.add(stringMap.get("WTMD"));
		
		//resourceSubsetMap.put(stringMap.get("ETD"), tempSet2);
		
		//Set<ScreeningResource> tempSet3 = new HashSet<ScreeningResource>();
		//tempSet3.add(stringMap.get("WTMD"));
		
		//resourceSubsetMap.put(stringMap.get("ETD"), tempSet3);
		
		Map<ScreeningResource, Set<ScreeningResource>> resourceSubsetMapCopy = createDeepCopy(resourceSubsetMap);
		
		for(ScreeningResource r1 : resourceSubsetMapCopy.keySet()){
			for(ScreeningResource r2 : resourceSubsetMapCopy.keySet()){
				if(resourceSubsetMap.get(r1).contains(r2)){
					for(ScreeningResource r3 : resourceSubsetMapCopy.get(r2)){
						resourceSubsetMap.get(r1).remove(r3);
					}
				}
			}
		}
		
		Map<ScreeningOperation, List<Vertex>> pointerMap = new HashMap<ScreeningOperation, List<Vertex>>();
		
		for(ScreeningResource r1 : resourceIntersectionList){
			Vertex defaultResourceVertex = new Vertex(r1 + "_" + 1, false);
			
			int i = 2;
			
			for(ScreeningOperation o : resourceOperationMap.get(r1)){
				Vertex currentResourceVertex = defaultResourceVertex;
				
				if(!pointerMap.containsKey(o)){
					pointerMap.put(o, new ArrayList<Vertex>());
				}
				
				boolean createNewVertex = false;
				
				for(ScreeningResource r2 : resourceIntersectionMap.get(r1).keySet()){
					if(resourceIntersectionMap.get(r1).get(r2).contains(o)){
						createNewVertex = true;
						
						resourceIntersectionMap.get(r1).get(r2).remove(o);
						resourceIntersectionMap.get(r2).get(r1).remove(o);
					}
				}
				
				if(createNewVertex){
					currentResourceVertex = new Vertex(r1 + "_" + i, false);
					i++;
				}
				
				pointerMap.get(o).add(currentResourceVertex);
			}	
		}
		
		List<ScreeningResource> resourceSubsetList = getResourceList(resourceSubsetMap);
		
		for(ScreeningResource r1 : resourceSubsetList){
			Vertex defaultResourceVertex = new Vertex(r1 + "_" + 1, false);
			
			for(ScreeningOperation o : resourceOperationMap.get(r1)){
				boolean addVertex = false;
				
				for(ScreeningResource r2 : resourceSubsetMapCopy.get(r1)){
					if(resourceOperationMap.get(r2).contains(o)){
						addVertex = true;
						break;
					}
				}
				
				if(addVertex){
					pointerMap.get(o).add(defaultResourceVertex);
				}
			}	
		}
		
		System.out.println(pointerMap);
	}
	
	Map<ScreeningResource, Set<ScreeningResource>> createDeepCopy(Map<ScreeningResource, Set<ScreeningResource>> map){
		Map<ScreeningResource, Set<ScreeningResource>> resourceSubsetMap = new HashMap<ScreeningResource, Set<ScreeningResource>>();
		
		for(ScreeningResource r1 : map.keySet()){
			Set<ScreeningResource> s1 = map.get(r1);
			
			Set<ScreeningResource> s2 = new HashSet<ScreeningResource>();
				
			for(ScreeningResource r2 : s1){
				s2.add(r2);
			}
			
			resourceSubsetMap.put(r1, s2);
		}
		
		return resourceSubsetMap;
	}
	
	public List<ScreeningResource> getResourceList(Map<ScreeningResource, Set<ScreeningResource>> resourceSubsetMap){
		List<ScreeningResource> screeningResources = new ArrayList<ScreeningResource>();
		
		for(ScreeningResource r1 : resourceSubsetMap.keySet()){
			for(ScreeningResource r2 : resourceSubsetMap.get(r1)){
				if(resourceSubsetMap.containsKey(r2)){
					getResourceList(resourceSubsetMap, screeningResources, r2);
				}
			}
			
			if(!screeningResources.contains(r1)){
				screeningResources.add(r1);
			}
			
			resourceSubsetMap.put(r1, new HashSet<ScreeningResource>());
		}
		
		return screeningResources;
	}
	
	public List<ScreeningResource> getResourceList(Map<ScreeningResource, Set<ScreeningResource>> resourceSubsetMap, List<ScreeningResource> screeningResources, ScreeningResource r1){
		for(ScreeningResource r2 : resourceSubsetMap.get(r1)){
			if(resourceSubsetMap.containsKey(r2)){
				getResourceList(resourceSubsetMap, screeningResources, r2);
			}
		}
			
		if(!screeningResources.contains(r1)){
			screeningResources.add(r1);
		}
			
		resourceSubsetMap.put(r1, new HashSet<ScreeningResource>());
		
		return screeningResources;
	}
	
	public PureStrategy samplePureStrategy(){
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> p
		 = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		for(int t : model.getTimeWindows()){
			totalResourceCapacityMap = model.getScreeningResourceCapacities().get(t);
			
			constructGraph(t);
			decomposeGraph();
			p.put(t, extractPureStrategy());
		}
		
		System.out.println(remainingResourceCapacityMap);
		System.out.println(remainingOperationCapacityMap);
		
		return new PureStrategy(p);
	}
	

	private void constructGraph(int t){
		GraphEdgeFactory edgeFactory = new GraphEdgeFactory();
		
		graph = new SimpleDirectedWeightedGraph<Vertex, Edge>(edgeFactory);
		edgeMap = new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Edge>>>();
		operationVertexMap = new HashMap<ScreeningOperation, Vertex>();
		resourceVertexMap = new HashMap<ScreeningResource, Vertex>();
		
		vertexOperationMap = new HashMap<Vertex, ScreeningOperation>();
		vertexResourceMap = new HashMap<Vertex, ScreeningResource>();
		
		Vertex sink = new Vertex("SINK", false);
		
		graph.addVertex(sink);
		
		for(ScreeningResource r : model.getScreeningResources().keySet()){
			Vertex v = new Vertex(t + "_" + r, false);
			
			resourceVertexMap.put(r, v);
			vertexResourceMap.put(v, r);
			
			graph.addVertex(v);
			
			Edge e = new Edge(v, sink, 0, true);
			graph.addEdge(v, sink, e);
				
			e = new Edge(sink, v, 0, false);
			graph.addEdge(sink, v, e);
		}
		
		for(ScreeningOperation o : model.getScreeningOperations()){
			Vertex v = new Vertex(t + "_" + o, true);
			
			operationVertexMap.put(o, v);
			vertexOperationMap.put(v, o);
			
			graph.addVertex(v);
			
			for(ScreeningResource r : o.getResources()){
				Edge e = new Edge(operationVertexMap.get(o), resourceVertexMap.get(r), 0, true);
				graph.addEdge(operationVertexMap.get(o), resourceVertexMap.get(r), e);
					
				e = new Edge(resourceVertexMap.get(r), operationVertexMap.get(o), 0, false);
				graph.addEdge(resourceVertexMap.get(r), operationVertexMap.get(o), e);
			}
		}
		
		for(Flight f : model.getFlights(t)){
			edgeMap.put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Edge>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				edgeMap.get(f).put(c, new HashMap<ScreeningOperation, Edge>());
				
				Vertex v1 = new Vertex(t + "_" + f + "_" + c, false);
				
				graph.addVertex(v1);
				
				for(ScreeningOperation o : model.getScreeningOperations()){
					Edge e = null;
					
					Vertex v2 = new Vertex(t + "_" + f + "_" + c + "_" + o, false);
					
					graph.addVertex(v2);
					
					double marginal = marginalStrategy.get(t).get(f).get(c).get(o);
					
					e = new Edge(v1, v2, marginal, true);
					graph.addEdge(v1, v2, e);
					
					edgeMap.get(f).get(c).put(o, e);
					
					e = new Edge(v2, v1, marginal, false);
					graph.addEdge(v2, v1, e);
					
					e = new Edge(v2, operationVertexMap.get(o), marginal, true);
					graph.addEdge(v2, operationVertexMap.get(o), e);
						
					e = new Edge(operationVertexMap.get(o), v2, marginal, false);
					graph.addEdge(operationVertexMap.get(o), v2, e);
				}
			}
		}
		
		for(ScreeningOperation o : model.getScreeningOperations()){
			double totalWeight = 0.0;
			
			for(Edge e : graph.incomingEdgesOf(operationVertexMap.get(o))){
				if(e.forwardEdge){
					totalWeight += e.getWeight();
				}
			}
			
			for(Edge e : graph.outgoingEdgesOf(operationVertexMap.get(o))){
				if(e.forwardEdge){
					e.setWeight(totalWeight);
					graph.getEdge(e.destination, e.source).setWeight(totalWeight);
				}
			}
		}
		
		for(ScreeningResource r : model.getScreeningResources().keySet()){
			double totalWeight = 0.0;
			
			for(Edge e : graph.incomingEdgesOf(resourceVertexMap.get(r))){
				if(e.forwardEdge){
					totalWeight += e.getWeight();
				}
			}
			
			for(Edge e : graph.outgoingEdgesOf(resourceVertexMap.get(r))){
				if(e.forwardEdge){
					e.setWeight(totalWeight);
					graph.getEdge(e.destination, e.source).setWeight(totalWeight);
				}
			}
			
			graph.getEdge(resourceVertexMap.get(r), sink).setWeight(totalWeight);
			graph.getEdge(sink, resourceVertexMap.get(r)).setWeight(totalWeight);
		}
	}
	
	private void decomposeGraph(){
		List<Edge> nonintegralEdges = new ArrayList<Edge>();
		
		for(Edge e : graph.edgeSet()){
			if(e.getWeight() % 1 > 0.0001 && e.getWeight() % 1 < 0.9999){
				nonintegralEdges.add(e);
			}
			
			if(e.forwardEdge){
				System.out.println(e + ": " + e.getWeight());
			}
		}
		
		remainingResourceCapacityMap = new HashMap<ScreeningResource, Double>();
		
		for(ScreeningResource r : resourceVertexMap.keySet()){
			double remainingResourceCapacity = totalResourceCapacityMap.get(r);
			
			for(Edge e : graph.incomingEdgesOf(resourceVertexMap.get(r))){
				if(e.forwardEdge){
					remainingResourceCapacity -= e.weight;
				}
			}
			
			remainingResourceCapacityMap.put(r, remainingResourceCapacity);
		}
		
		remainingOperationCapacityMap = new HashMap<ScreeningOperation, Double>();
		
		for(ScreeningOperation o : operationVertexMap.keySet()){
			double remainingOperationCapacity = Double.POSITIVE_INFINITY;
			
			for(ScreeningResource r : o.getResources()){
				if(remainingResourceCapacityMap.get(r) < remainingOperationCapacity){
					remainingOperationCapacity = remainingResourceCapacityMap.get(r);
				}
			}
			
			remainingOperationCapacityMap.put(o, remainingOperationCapacity);
		}
		
		while(nonintegralEdges.size() > 0){
			System.out.println("Computing Cycles... Started");
			
			Collections.shuffle(nonintegralEdges);
			
			List<Edge> cycle = null;
			
			for(Edge e : nonintegralEdges){
				System.out.println("Selected Edge: " + e);
					
				cycle = findCycle(e.source, e.destination, nonintegralEdges, new ArrayList<Vertex>());
				
				if(cycle != null){
					break;
				}
			}
				
			System.out.println("Computing Cycles... Completed");
			
			//if(cycle == null){
			//	System.out.println("NO CYCLE FOUND");
			//	
			//	return;
			//}
			
			double alpha = calculateAdjustment(cycle, true);
			double beta = calculateAdjustment(cycle, false);
			
			double prob = beta / (alpha + beta);
			
			double sampledProb = random.nextDouble();
			
			System.out.println("# Non-Integral Edges: " + nonintegralEdges.size());
			System.out.println("Alpha: " + alpha);
			System.out.println("Beta: " + beta);
			System.out.println("Prob1: " + prob);
			System.out.println("Prob2: " + (1.0 - prob));
			System.out.println("Sampled: " + sampledProb);
			
			if(sampledProb < prob){
				applyAdjustment(cycle, alpha, true);
				
				System.out.println("Adjustment:" + alpha);
			}
			else{
				applyAdjustment(cycle, beta, false);
				
				System.out.println("Adjustment:" + beta);
			}
			
			System.out.println("******************");
			
			nonintegralEdges = new ArrayList<Edge>();
			
			for(Edge edge : graph.edgeSet()){
				if(edge.getWeight() % 1 > 0.0001 && edge.getWeight() % 1 < 0.9999){
					nonintegralEdges.add(edge);
				}
				
				if(edge.forwardEdge && edge.destination.operationVertex){
					System.out.println("Assignment: " + edge + " " + edge.getWeight());
				}
			}
			
			remainingResourceCapacityMap = new HashMap<ScreeningResource, Double>();
			
			for(ScreeningResource r : resourceVertexMap.keySet()){
				double remainingResourceCapacity = totalResourceCapacityMap.get(r);
				
				for(Edge e : graph.incomingEdgesOf(resourceVertexMap.get(r))){
					if(e.forwardEdge){
						remainingResourceCapacity -= e.weight;
					}
				}
				
				remainingResourceCapacityMap.put(r, remainingResourceCapacity);
			}
			
			remainingOperationCapacityMap = new HashMap<ScreeningOperation, Double>();
			
			for(ScreeningOperation o : operationVertexMap.keySet()){
				double remainingOperationCapacity = Double.POSITIVE_INFINITY;
				
				for(ScreeningResource r : o.getResources()){
					if(remainingResourceCapacityMap.get(r) < remainingOperationCapacity){
						remainingOperationCapacity = remainingResourceCapacityMap.get(r);
					}
				}
				
				remainingOperationCapacityMap.put(o, remainingOperationCapacity);
			}
			
			System.out.println(remainingResourceCapacityMap);
			System.out.println(remainingOperationCapacityMap);
		}
	}
	
	private List<Edge> findCycle(Vertex startVertex, Vertex currentVertex, List<Edge> nonintegralEdges, List<Vertex> visitedVertices){
		visitedVertices.add(currentVertex);
		
		for(Edge edge : graph.outgoingEdgesOf(currentVertex)){
			if(nonintegralEdges.contains(edge)){
				if(edge.destination == startVertex && visitedVertices.size() == 1){
					continue;
				}
				else if(edge.destination == startVertex && visitedVertices.size() > 1){
					List<Edge> cycle = new ArrayList<Edge>();
					
					visitedVertices.add(0, startVertex);
					visitedVertices.add(startVertex);
					
					System.out.println(visitedVertices);
					
					for(int i = 0; i < visitedVertices.size() - 1; i++){
						Edge e = graph.getEdge(visitedVertices.get(i), visitedVertices.get(i + 1));
						
						if(e.forwardEdge && e.source.operationVertex){
							for(Edge e1 : graph.outgoingEdgesOf(visitedVertices.get(i))){
								if(e1.forwardEdge){
									cycle.add(e1);
									System.out.println("Adding edge: " + e1 + " " + e1.weight);
								}
							}
						}
						else if(!e.forwardEdge && e.destination.operationVertex){
							for(Edge e1 : graph.incomingEdgesOf(visitedVertices.get(i + 1))){
								if(!e1.forwardEdge){
									cycle.add(e1);
									System.out.println("Adding edge: " + e1 + " " + e1.weight);
								}
							}
						}
						else{
							cycle.add(e);
							System.out.println("Adding edge: " + e + " " + e.weight);
						}
					}
					
					return cycle;
				}
				else if(!visitedVertices.contains(edge.destination)){
					boolean validEdge = true;
					
					if(edge.forwardEdge && edge.source.operationVertex){
						ScreeningOperation o = vertexOperationMap.get(edge.source);
						
						if(remainingOperationCapacityMap.get(o) <= 0.000001){
							validEdge = false;
						}
					}
					else if(!edge.forwardEdge && edge.destination.operationVertex){
						ScreeningOperation o = vertexOperationMap.get(edge.destination);
						
						if(remainingOperationCapacityMap.get(o) <= 0.000001){
							validEdge = false;
						}
					}
					
					if(validEdge){
						List<Edge> cycle = findCycle(startVertex, edge.destination, nonintegralEdges, new ArrayList<Vertex>(visitedVertices));
						
						if(cycle != null){
							return cycle;
						}
					}
				}
			}
		}
		
		return null;
	}
	
	private double calculateAdjustment(List<Edge> cycle, boolean forwardCycle){
		double minAdjustment = Double.POSITIVE_INFINITY;
		
		Set<ScreeningResource> inResources = new HashSet<ScreeningResource>();
		Set<ScreeningResource> outResources = new HashSet<ScreeningResource>();
		
		
		for(Edge e : cycle){
			double adjustment;
			
			if(e.forwardEdge == forwardCycle){
				adjustment = 1.0 - (e.getWeight() % 1);
			
				if(e.forwardEdge && e.source.operationVertex){
					inResources.add(vertexResourceMap.get(e.destination));
				}
				else if(!e.forwardEdge && e.destination.operationVertex){
					inResources.add(vertexResourceMap.get(e.source));
				}
			}
			else{
				adjustment = e.getWeight() % 1;
				
				if(e.forwardEdge && e.source.operationVertex){
					outResources.add(vertexResourceMap.get(e.destination));
				}
				else if(!e.forwardEdge && e.destination.operationVertex){
					outResources.add(vertexResourceMap.get(e.source));
				}
			}
			
			if(adjustment < minAdjustment){
				minAdjustment = adjustment;
			}
		}
		
		/*
		for(ScreeningResource r : inResources){
			if(!outResources.contains(r)){
				if(minAdjustment > remainingResourceCapacityMap.get(r)){
					minAdjustment = remainingResourceCapacityMap.get(r);
				}
			}
		}
		*/
		
		return minAdjustment;
	}
	
	private void applyAdjustment(List<Edge> cycle, double adjustment, boolean forwardCycle){
		Set<Edge> cycleSet = new HashSet<Edge>(cycle);
		
		if(cycle.size() != cycleSet.size()){
			System.out.println("got here");
		}
		
		for(Edge e : cycleSet){
			Edge e1 = e;
			Edge e2 = graph.getEdge(e.destination, e.source);
			
			if(e1.forwardEdge == forwardCycle){
				e1.setWeight(e1.getWeight() + adjustment);
				e2.setWeight(e2.getWeight() + adjustment);
			}
			else{
				e1.setWeight(e1.getWeight() - adjustment);
				e2.setWeight(e2.getWeight() - adjustment);
			}
			
			System.out.println("Setting Weight: " + e1 + " " + e1.weight);
			System.out.println("Setting Weight: " + e2 + " " + e2.weight);
		}
	}
	
	private Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>> extractPureStrategy(){
		Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>> p = new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>();
		
		for(Flight f : edgeMap.keySet()){
			p.put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
			
			for(RiskCategory c : edgeMap.get(f).keySet()){
				p.get(f).put(c, new HashMap<ScreeningOperation, Integer>());
				
				for(ScreeningOperation o : edgeMap.get(f).get(c).keySet()){
					double weight = edgeMap.get(f).get(c).get(o).weight;
					
					if(weight % 1 > 0.9999){
						p.get(f).get(c).put(o, (int)(weight) + 1);
					}
					else{
						p.get(f).get(c).put(o, (int)weight);
					}
				}
			}
		}
		
		return p;
	}
	
	public static class Vertex{
		private String description;
		private boolean operationVertex;
		
		public Vertex(String description, boolean resourceVertex){
			this.description = description;
			this.operationVertex = resourceVertex;
		}
		
		public String toString(){
			return description;
		}
	}
	
	public static class Edge{
		private Vertex source;
		private Vertex destination;
		private boolean forwardEdge;
		private double weight;
		
		public Edge(Vertex v1, Vertex v2){
			this.source = v1;
			this.destination = v2;
		}
		
		public Edge(Vertex v1, Vertex v2, double weight, boolean forwardEdge){
			this.source = v1;
			this.destination = v2;
			this.weight = weight;
			this.forwardEdge = forwardEdge;
		}
		
		public void setWeight(double weight){
			this.weight = weight;
		}
		
		public double getWeight(){
			return weight;
		}
		
		public String toString(){
			return source + "->" + destination + "(" + forwardEdge + ")";
		}
	}
	
	public static class GraphEdgeFactory implements EdgeFactory<Vertex,Edge>{
		public GraphEdgeFactory(){
			
		}
		
		public Edge createEdge(Vertex v1, Vertex v2){
			return new Edge(v1, v2);
		}
	}
}
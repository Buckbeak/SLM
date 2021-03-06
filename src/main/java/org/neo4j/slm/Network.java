package org.neo4j.slm; /**
 * Network
 *
 * @author Ludo Waltman
 * @author Nees Jan van Eck
 * @version 1.2.0, 05/14/14
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.neo4j.graphdb.Result;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;

public class Network implements Cloneable, Serializable
{
    private static final long serialVersionUID = 1;

    private double totalEdgeWeightSelfLinks;
    private int numberOfClusters;
    private boolean clusteringStatsAvailable;

    private Map<Integer, Node> nodes;
    private Clusters clusters;

    public Network( Map<Integer, Node> nodes )
    {
        this.nodes = nodes;
    }

    public static Network create( ModularityOptimizer.ModularityFunction modularityFunction, Result result )
            throws IOException
    {
        double[] edgeWeight;
        int i, nEdges;
        int[] neighbor;

        List<Relationship> relationships = new ArrayList<>( 10_000 );
        Map<Integer, Node> nodes = new TreeMap<>();

        /*
            Reads the input file/stream and creates a map of (id -> node) and a list of relationships
            Could replace this with a stream of results coming from a Cypher query?
            Step 1. Use all the machinery and sub in Cypher query output
            Step 2. Remove unnecessary conversion and just use Neo4j data types
         */

        while ( result.hasNext() )
        {
            Map<String, Object> row = result.next();

            int sourceId = parseInt( (String) row.get( "p1" ) );
            int destinationId = parseInt( (String) row.get( "p2" ) );
            double weight = (double) row.get( "weight" );

            Node source = nodes.get( sourceId );
            if ( source == null )
            {
                source = new Node( sourceId );
                nodes.put( sourceId, source );
            }

            Node destination = nodes.get( destinationId );
            if ( destination == null )
            {
                destination = new Node( destinationId );
                nodes.put( destinationId, destination );
            }

            destination.in( source, weight );
            source.out( destination, weight );
        }

        return modularityFunction.createNetwork( nodes );
    }

    public static Network create( ModularityOptimizer.ModularityFunction modularityFunction, Reader in )
            throws IOException
    {
        double[] edgeWeight;
        int i, nEdges;
        int[] neighbor;

        List<Relationship> relationships = new ArrayList<>( 10_000 );
        Map<Integer, Node> nodes = new TreeMap<>();

        /*
            Reads the input file/stream and creates a map of (id -> node) and a list of relationships
            Could replace this with a stream of results coming from a Cypher query?
            Step 1. Use all the machinery and sub in Cypher query output
            Step 2. Remove unnecessary conversion and just use Neo4j data types
         */

        int internalNodeId = 0;
        try ( BufferedReader bufferedReader = new BufferedReader( in ) )
        {
            String line;
            while ( (line = bufferedReader.readLine()) != null )
            {
                if ( line.length() == 0 )
                {
                    break;
                }

                String[] parts = line.split( "\t" );

                int sourceId = parseInt( parts[0] );
                int destinationId = parseInt( parts[1] );

                Node source = nodes.get( sourceId );
                if ( source == null )
                {
                    source = new Node( sourceId );
                    nodes.put( sourceId, source );
                }

                Node destination = nodes.get( destinationId );
                if ( destination == null )
                {
                    destination = new Node( destinationId );
                    nodes.put( destinationId, destination );
                }
                double weight = (parts.length > 2) ? parseDouble( parts[2] ) : 1;
                destination.in( source, weight );
                source.out( destination, weight );

                relationships.add( Relationship.from( line ) );
            }
        }

        /*
         Calculate a few metrics around the nodes
         */
        int numberOfNodes = nodes.size();
        int[] degree = degree( nodes );
        int[] firstNeighborIndex = new int[numberOfNodes + 1];
        nEdges = 0;
        for ( i = 0; i < numberOfNodes; i++ )
        {
            firstNeighborIndex[i] = nEdges;
            nEdges += degree[i];
        }
        firstNeighborIndex[numberOfNodes] = nEdges;

        neighbor = new int[nEdges];
        edgeWeight = new double[nEdges];

        Arrays.fill( degree, 0 );

        for ( Relationship relationship : relationships )
        {
            if ( relationship.getSource() < relationship.getDestination() )
            {
                int j = firstNeighborIndex[relationship.getSource()] + degree[relationship.getSource()];
                neighbor[j] = relationship.getDestination();
                edgeWeight[j] = relationship.getWeight();
                degree[relationship.getSource()]++;

                j = firstNeighborIndex[relationship.getDestination()] + degree[relationship.getDestination()];
                neighbor[j] = relationship.getSource();
                edgeWeight[j] = relationship.getWeight();
                degree[relationship.getDestination()]++;
            }
        }

        return modularityFunction.createNetwork(
                nodes );
    }

    private static int[] degree( Map<Integer, Node> nodesMap )
    {
        int[] numberOfNeighbours = new int[nodesMap.size()];

        for ( Map.Entry<Integer, Node> entry : nodesMap.entrySet() )
        {
            numberOfNeighbours[entry.getKey()] = entry.getValue().degree();
        }

        return numberOfNeighbours;
    }


    public int getNNodes()
    {
        return nodes.size();
    }

    public int getNEdges()
    {
        int edges = 0;
        for ( Node node : nodes.values() )
        {
            for ( Relationship ignored : node.relationships() )
            {
                edges++;
            }
        }

        return edges;
    }

    public double getTotalEdgeWeight()
    {
        double totalEdgeWeights = 0.0;
        for ( Node node : nodes.values() )
        {
            for ( Relationship relationship : node.relationships() )
            {
                totalEdgeWeights += relationship.getWeight();
            }
        }

        return totalEdgeWeights;
    }

    public double[] getEdgeWeights()
    {
        double[] edgeWeights = new double[getNEdges()];
        int i = 0;
        for ( Node node : nodes.values() )
        {
            for ( Relationship relationship : node.relationships() )
            {
                edgeWeights[i] = relationship.getWeight();
                i++;
            }
        }
        return edgeWeights;
    }

    public double[] getNodeWeights()
    {
        double[] nodeWeight = new double[nodes.size()];
        for ( Map.Entry<Integer, Node> entry : nodes.entrySet() )
        {
            nodeWeight[entry.getKey()] = entry.getValue().weight();
        }

        return nodeWeight;
    }

    public int getNClusters()
    {
        return numberOfClusters;
    }

    public int[] getClusters()
    {
        int[] clusters = new int[nodes.size()];
        int idx = 0;
        for ( Node node : nodes.values() )
        {
            clusters[idx] = node.getCluster();
            idx++;
        }

        return clusters;
    }

    public Map<Integer, Node> getNodes()
    {
        return nodes;
    }

    public void initSingletonClusters()
    {
        int i;

        numberOfClusters = nodes.size();
        for ( i = 0; i < nodes.size(); i++ )
        {
            updateCluster( i, i );
        }

        deleteClusteringStats();
        calcClusteringStats();
    }

    private void updateCluster( int index, int value )
    {
        nodes.get( nodeIds()[index] ).setCluster( value );
    }

    private int[] nodeIds()
    {
        int[] nodeIds = new int[nodes.size()];
        int idx = 0;
        for ( Node node : nodes.values() )
        {
            nodeIds[idx] = node.nodeId;
            idx++;
        }
        return nodeIds;
    }

    private static void print( int[] items )
    {
        for ( int item : items )
        {
            System.out.print( item + " " );
        }
        System.out.println();
    }

    public void mergeClusters( int[] newCluster )
    {
        int i = 0;
        for ( int j = 0; j < nodes.size(); j++ )
        {
            int k = newCluster[clusterByIndex( j )];
            if ( k > i )
            {
                i = k;
            }
            updateCluster( j, k );
        }
        numberOfClusters = i + 1;

        deleteClusteringStats();
    }

    public void orderClustersByNNodes()
    {
        orderClusters( false );
    }

    public Network[] createSubnetworks()
    {
        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        Network[] subnetwork = new Network[numberOfClusters];

        for ( int clusterId = 0; clusterId < numberOfClusters; clusterId++ )
        {
            subnetwork[clusterId] = createSubnetwork( clusterId );
        }

        return subnetwork;
    }

    public ReducedNetwork calculateReducedNetwork()
    {
        double[] reducedNetworkEdgeWeight1, reducedNetworkEdgeWeight2;
        int clusterId, index, nodeId, l, otherNodeClusterId, reducedNetworkNEdges1, reducedNetworkNEdges2;
        int[] reducedNetworkNeighbor1, reducedNetworkNeighbor2;

        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        ReducedNetwork reducedNetwork = new ReducedNetwork();

        reducedNetwork.numberOfNodes = numberOfClusters;
        reducedNetwork.firstNeighborIndex = new int[numberOfClusters + 1];
        reducedNetwork.totalEdgeWeightSelfLinks = totalEdgeWeightSelfLinks;
        reducedNetwork.nodeWeight = new double[numberOfClusters];

        reducedNetworkNeighbor1 = new int[getNEdges()];
        reducedNetworkEdgeWeight1 = new double[getNEdges()];

        reducedNetworkNeighbor2 = new int[numberOfClusters - 1];
        reducedNetworkEdgeWeight2 = new double[numberOfClusters];

        reducedNetworkNEdges1 = 0;
        for ( clusterId = 0; clusterId < numberOfClusters; clusterId++ )
        {
            reducedNetworkNEdges2 = 0;
            for ( index = 0; index < clusters.get( clusterId ).numberOfNodes(); index++ )
            {
                nodeId = clusters.get( clusterId ).nodesIds()[index];

                for ( Relationship relationship : nodes.get( nodeId ).relationships() )
                {
                    int otherNodeId = relationship.otherNode( nodeId );
                    otherNodeClusterId = clusters.findClusterId( otherNodeId );
                    if ( otherNodeClusterId != clusterId )
                    {
                        if ( reducedNetworkEdgeWeight2[otherNodeClusterId] == 0 )
                        {
                            reducedNetworkNeighbor2[reducedNetworkNEdges2] = otherNodeClusterId;
                            reducedNetworkNEdges2++;
                        }
                        reducedNetworkEdgeWeight2[otherNodeClusterId] += relationship.getWeight();
                    }
                    else
                    {
                        reducedNetwork.totalEdgeWeightSelfLinks += relationship.getWeight();
                    }
                }

//                reducedNetwork.nodeWeight[clusterId] += nodeWeight( nodeId );
                reducedNetwork.nodeWeight[clusterId] += nodes.get( nodeId ).weight();
            }

            for ( index = 0; index < reducedNetworkNEdges2; index++ )
            {
                reducedNetworkNeighbor1[reducedNetworkNEdges1 + index] = reducedNetworkNeighbor2[index];
                reducedNetworkEdgeWeight1[reducedNetworkNEdges1 + index] =
                        reducedNetworkEdgeWeight2[reducedNetworkNeighbor2[index]];
                reducedNetworkEdgeWeight2[reducedNetworkNeighbor2[index]] = 0;
            }
            reducedNetworkNEdges1 += reducedNetworkNEdges2;

            reducedNetwork.firstNeighborIndex[clusterId + 1] = reducedNetworkNEdges1;
        }

        reducedNetwork.neighbor = new int[reducedNetworkNEdges1];
        reducedNetwork.edgeWeight = new double[reducedNetworkNEdges1];
        System.arraycopy( reducedNetworkNeighbor1, 0, reducedNetwork.neighbor, 0, reducedNetworkNEdges1 );
        System.arraycopy( reducedNetworkEdgeWeight1, 0, reducedNetwork.edgeWeight, 0, reducedNetworkNEdges1 );

        return reducedNetwork;
    }

    public double calcQualityFunction( double resolution )
    {
        double qualityFunction, totalEdgeWeight;

        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        qualityFunction = totalEdgeWeightSelfLinks;
        totalEdgeWeight = totalEdgeWeightSelfLinks;

        for ( Map.Entry<Integer, Node> entry : nodes.entrySet() )
        {
            int clusterId = clusters.findClusterId( entry.getKey() );
            for ( Relationship relationship : entry.getValue().relationships() )
            {
                int otherNodeId = relationship.otherNode( entry.getKey() );
                if ( clusters.findClusterId( otherNodeId ) == clusterId )
                {
                    qualityFunction += relationship.getWeight();
                }
                totalEdgeWeight += relationship.getWeight();
            }
        }

        for ( int clusterId = 0; clusterId < numberOfClusters; clusterId++ )
        {
            qualityFunction -= clusters.get( clusterId ).weight() * clusters.get( clusterId ).weight() * resolution;
        }

        qualityFunction /= totalEdgeWeight;

        return qualityFunction;
    }

    public boolean runLocalMovingAlgorithm( double resolution, Random random )
    {
        double qualityFunction;
        int[] newCluster;

        if ( (nodes.size() == 1) )
        {
            return false;
        }

        boolean update = false;

        Map<Integer, Cluster> clusters = new HashMap<>();
        for ( int i = 0; i < nodes.size(); i++ )
        {
            int clusterId = clusterByIndex( i );
            Cluster cluster = clusters.get( clusterId );
            if ( cluster == null )
            {
                cluster = new Cluster( clusterId );
                clusters.put( clusterId, cluster );
            }
            cluster.addWeight( nodeWeight( i ) );
        }

        int[] numberOfNodesPerCluster = this.clusters.nodesPerCluster( nodes.size() );

        int numberUnusedClusters = 0;
        int[] unusedCluster = new int[nodes.size()];
        for ( int i = 0; i < nodes.size(); i++ )
        {
            if ( numberOfNodesPerCluster[i] == 0 )
            {
                unusedCluster[numberUnusedClusters] = i;
                numberUnusedClusters++;
            }
        }

        int[] nodesInRandomOrder = nodesInRandomOrder( nodes.size(), random );

        double[] edgeWeightsPointingToCluster = new double[nodes.size()];
        int[] neighboringCluster = new int[nodes.size() - 1];

        int numberStableNodes = 0;
        int i = 0;
        do
        {
            int nodeId = nodesInRandomOrder[i];
            BestCluster bc =
                    findBestCluster( resolution, clusters, numberOfNodesPerCluster, numberUnusedClusters, unusedCluster,
                            edgeWeightsPointingToCluster, neighboringCluster, nodeId );
            numberUnusedClusters = bc.numberUnusedClusters;

            clusters.get( bc.bestCluster ).addWeight( nodeWeight( nodeId ) );
            numberOfNodesPerCluster[bc.bestCluster]++;
            if ( bc.bestCluster == clusterByIndex( nodeId ) )
            {
                numberStableNodes++;
            }
            else
            {
                updateCluster( nodeId, bc.bestCluster );

                numberStableNodes = 1;
                update = true;
            }

            i = (i < nodes.size() - 1) ? (i + 1) : 0;
        }
        while ( numberStableNodes < nodes.size() );

        newCluster = new int[nodes.size()];
        numberOfClusters = 0;
        for ( i = 0; i < nodes.size(); i++ )
        {
            if ( numberOfNodesPerCluster[i] > 0 )
            {
                newCluster[i] = numberOfClusters;
                numberOfClusters++;
            }
        }

        for ( i = 0; i < nodes.size(); i++ )
        {
            updateCluster( i, newCluster[clusterByIndex( i )] );
        }

        deleteClusteringStats();

        return update;
    }

    private BestCluster findBestCluster( double resolution, Map<Integer, Cluster> clusters,
                                         int[] numberOfNodesPerCluster, int numberUnusedClusters, int[] unusedCluster,
                                         double[] edgeWeightsPointingToCluster, int[] neighboringCluster, int nodeId )
    {
        Node node = nodes.get( nodeIds()[nodeId] );

        double qualityFunction;
        int numberOfNeighbouringClusters = 0;
        for ( Relationship relationship : node.relationships() )
        {
            Node neighbour = nodes.get( relationship.otherNode( node.nodeId ) );
            if ( neighbour != null )
            {
                int neighbourClusterId = neighbour.getCluster();
                if ( edgeWeightsPointingToCluster[neighbourClusterId] == 0 )
                {
                    neighboringCluster[numberOfNeighbouringClusters] = neighbourClusterId;
                    numberOfNeighbouringClusters++;
                }
                edgeWeightsPointingToCluster[neighbourClusterId] += relationship.getWeight();
            }
        }

        clusters.get( clusterByIndex( nodeId ) ).removeWeight( nodeWeight( nodeId ) );
        numberOfNodesPerCluster[clusterByIndex( nodeId )]--;
        if ( numberOfNodesPerCluster[clusterByIndex( nodeId )] == 0 )
        {
            unusedCluster[numberUnusedClusters] = clusterByIndex( nodeId );
            numberUnusedClusters++;
        }

        int bestCluster = -1;
        double maxQualityFunction = 0;

        // work out the best cluster to place this node in
        for ( int neighbouringClusterIndex = 0; neighbouringClusterIndex < numberOfNeighbouringClusters;
              neighbouringClusterIndex++ )
        {
            int clusterId = neighboringCluster[neighbouringClusterIndex];
            qualityFunction = edgeWeightsPointingToCluster[clusterId] -
                    nodeWeight( nodeId ) * clusters.get( clusterId ).weight * resolution;
            if ( (qualityFunction > maxQualityFunction) ||
                    ((qualityFunction == maxQualityFunction) && (clusterId < bestCluster)) )
            {
                bestCluster = clusterId;
                maxQualityFunction = qualityFunction;
            }
            edgeWeightsPointingToCluster[clusterId] = 0;
        }
        if ( maxQualityFunction == 0 )
        {
            bestCluster = unusedCluster[numberUnusedClusters - 1];
            numberUnusedClusters--;
        }

        return new BestCluster( bestCluster, numberUnusedClusters );
    }

    private double nodeWeight( int nodeId )
    {
        return nodes.get( nodeIds()[nodeId] ).weight();
    }

    private int[] nodesInRandomOrder( int numberOfNodes, Random random )
    {
        int[] nodeOrder = new int[numberOfNodes];
        for ( int i = 0; i < numberOfNodes; i++ )
        {
            nodeOrder[i] = i;
        }

        for ( int i = 0; i < numberOfNodes; i++ )
        {
            int j = random.nextInt( numberOfNodes );
            int k = nodeOrder[i];
            nodeOrder[i] = nodeOrder[j];
            nodeOrder[j] = k;
        }
        return nodeOrder;
    }

    public boolean runLouvainAlgorithm( double resolution, Random random )
    {
        boolean update, update2;

        if ( (nodes.size() == 1) )
        {
            return false;
        }

        update = runLocalMovingAlgorithm( resolution, random );

        if ( numberOfClusters < nodes.size() )
        {
            ReducedNetwork reducedNetwork = calculateReducedNetwork();
            reducedNetwork.initSingletonClusters();

            update2 = reducedNetwork.runLouvainAlgorithm( resolution, random );

            if ( update2 )
            {
                update = true;

                mergeClusters( reducedNetwork.getClusters() );
            }
        }

        deleteClusteringStats();

        return update;
    }

    public boolean runLouvainAlgorithmWithMultilevelRefinement( double resolution, Random random )
    {
        boolean update, update2;

        if ( (nodes.size() == 1) )
        {
            return false;
        }

        update = runLocalMovingAlgorithm( resolution, random );

        if ( numberOfClusters < nodes.size() )
        {
            ReducedNetwork reducedNetwork = calculateReducedNetwork();
            reducedNetwork.initSingletonClusters();

            update2 = reducedNetwork.runLouvainAlgorithm( resolution, random );

            if ( update2 )
            {
                update = true;

                mergeClusters( reducedNetwork.getClusters() );

                runLocalMovingAlgorithm( resolution, random );
            }
        }

        deleteClusteringStats();

        return update;
    }

    public boolean runSmartLocalMovingAlgorithm( double resolution, Random random )
    {
        if ( (nodes.size() == 1) )
        {
            return false;
        }

        boolean update = runLocalMovingAlgorithm( resolution, random );
        // after this cluster is updated with the 8 clusters

        if ( numberOfClusters < nodes.size() )
        {
            if ( !clusteringStatsAvailable )
            {
                calcClusteringStats();
            }

            Network[] subnetworks = createSubnetworks();

            numberOfClusters = 0;
            for ( int subnetworkId = 0; subnetworkId < subnetworks.length; subnetworkId++ )
            {
                // need to add the nodes map to the sub network
                Network subnetwork = subnetworks[subnetworkId];

                subnetwork.initSingletonClusters();
                subnetwork.runLocalMovingAlgorithm( resolution, random );

                int[] subnetworkCluster = subnetwork.getClusters();
                for ( int nodeIndex = 0; nodeIndex < subnetworkCluster.length; nodeIndex++ )
                {
                    // sub network has a bunch of nodes in it
                    // we want to update the top level network with the new cluster values
                    // the clusters within the sub network are 0 based so we need to convert their values to be absolute instead of relative
                    int nodeId = clusters.get( subnetworkId ).nodesIds()[nodeIndex];
                    int newClusterId = numberOfClusters + subnetworkCluster[nodeIndex];
                    nodes.get( nodeId ).setCluster( newClusterId );

//                    updateCluster( nodeId, newClusterId );
                }
                numberOfClusters += subnetwork.getNClusters();
            }
            calcClusteringStats();

            ReducedNetwork reducedNetwork = calculateReducedNetwork();
            int[] reducedNetworkCluster = new int[numberOfClusters];
            int i = 0;
            for ( int j = 0; j < subnetworks.length; j++ )
            {
                for ( int k = 0; k < subnetworks[j].getNClusters(); k++ )
                {
                    reducedNetworkCluster[i] = j;
                    i++;
                }
            }

            reducedNetwork.setClusters( reducedNetworkCluster );
            update |= reducedNetwork.runSmartLocalMovingAlgorithm( resolution, random );
            mergeClusters( reducedNetwork.getClusters() );
        }

        deleteClusteringStats();

        return update;
    }

    private Network()
    {
    }

    private void writeObject( ObjectOutputStream out ) throws IOException
    {
        deleteClusteringStats();

        out.defaultWriteObject();
    }

    private void orderClusters( boolean orderByWeight )
    {
        class ClusterSize implements Comparable<ClusterSize>
        {
            public int cluster;
            public double size;

            public ClusterSize( int cluster, double size )
            {
                this.cluster = cluster;
                this.size = size;
            }

            public int compareTo( ClusterSize cluster )
            {
                return (cluster.size > size) ? 1 : ((cluster.size < size) ? -1 : 0);
            }
        }

        ClusterSize[] clusterSize;
        int i;
        int[] newCluster;


        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        clusterSize = new ClusterSize[numberOfClusters];
        for ( i = 0; i < numberOfClusters; i++ )
        {
            clusterSize[i] = new ClusterSize( i,
                    orderByWeight ? clusters.get( i ).weight() : clusters.get( i ).numberOfNodes() );
        }

        Arrays.sort( clusterSize );

        newCluster = new int[numberOfClusters];
        i = 0;
        do
        {
            newCluster[clusterSize[i].cluster] = i;
            i++;
        }
        while ( (i < numberOfClusters) && (clusterSize[i].size > 0) );
        numberOfClusters = i;
        for ( i = 0; i < nodes.size(); i++ )
        {
            updateCluster( i, newCluster[clusterByIndex( i )] );
        }

        deleteClusteringStats();
    }

    private Network createSubnetwork( int clusterId )
    {
        // this currently isn't being set on a reduced network
        // that seems to behave differently though as I don't think the network represents actual nodes, but
        // rather groups of them
        Map<Integer, Node> newNodes = new TreeMap<>();
        for ( int nodeId : clusters.get( clusterId ).nodesIds() )
        {
            Node node = nodes.get( nodeId );
            newNodes.put( nodeId, node );
        }
        Network subnetwork = new Network( newNodes );
        subnetwork.totalEdgeWeightSelfLinks = 0;

        return subnetwork;
    }

    // get the cluster for a node in a specific position
    private int clusterByIndex( int index )
    {
        return nodes.get( nodeIds()[index] ).getCluster();
    }

    private void calcClusteringStats()
    {
        clusters = new Clusters();

        for ( Map.Entry<Integer, Node> entry : nodes.entrySet() )
        {
            Node node = entry.getValue();
            int clusterId = node.getCluster();
            Cluster cluster = clusters.get( clusterId );
            if ( cluster == null )
            {
                cluster = new Cluster( clusterId );
                clusters.put( clusterId, cluster );
            }
            cluster.addNode( node );
        }

        clusteringStatsAvailable = true;
    }

    private void deleteClusteringStats()
    {
        clusteringStatsAvailable = false;
    }

    class Cluster
    {
        private int clusterId;
        private double weight = 0.0;
        private List<Node> nodes = new ArrayList<>();

        public Cluster( int clusterId )
        {
            this.clusterId = clusterId;
        }

        public void addWeight( double weight )
        {
            this.weight += weight;
        }


        public int[] nodesIds()
        {
            int[] nodesArray = new int[nodes.size()];
            int index = 0;
            for ( Node node : nodes )
            {
                nodesArray[index] = node.nodeId;
                index++;
            }
            return nodesArray;
        }


        public void removeWeight( double weight )
        {
            this.weight -= weight;
        }

        public void addNode( Node node )
        {
            this.nodes.add( node );
        }

        public double weight()
        {
            double weight = 0.0;
            for ( Node node : nodes )
            {
                weight += node.weight();
            }
            return weight;
        }

        public int numberOfNodes()
        {
            return nodes.size();
        }

        public List<Node> nodes()
        {
            return nodes;
        }
    }

    public static class BestCluster
    {

        private final int bestCluster;
        private final int numberUnusedClusters;

        public BestCluster( int bestCluster, int numberUnusedClusters )
        {

            this.bestCluster = bestCluster;
            this.numberUnusedClusters = numberUnusedClusters;
        }
    }
}

package org.neo4j.slm; /**
 * Network
 *
 * @author Ludo Waltman
 * @author Nees Jan van Eck
 * @version 1.2.0, 05/14/14
 */

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;

public class ReducedNetwork implements Cloneable, Serializable
{

    private Map<Integer,Node> nodesMap;

    private static final long serialVersionUID = 1;

    int numberOfNodes;
    int[] firstNeighborIndex;
    int[] neighbor;
    double[] edgeWeight;
    double totalEdgeWeightSelfLinks;
    double[] nodeWeight;
    int numberOfClusters;
    private int[] cluster;

    double[] clusterWeight;
    private int[] numberNodesPerCluster;
    private int[][] nodePerCluster;
    private boolean clusteringStatsAvailable;

    public int[] getNeighbor()
    {
        return neighbor;
    }

    public static ReducedNetwork load( String fileName ) throws ClassNotFoundException, IOException
    {
        ReducedNetwork network;
        ObjectInputStream objectInputStream;

        objectInputStream = new ObjectInputStream( new FileInputStream( fileName ) );

        network = (ReducedNetwork) objectInputStream.readObject();

        objectInputStream.close();

        return network;
    }

    public ReducedNetwork( int numberOfNodes, int[][] edge )
    {
        this( numberOfNodes, edge, null, null, null, null );
    }

    public ReducedNetwork( int numberOfNodes, int[][] edge, double[] edgeWeight )
    {
        this( numberOfNodes, edge, edgeWeight, null, null, null );
    }

    public ReducedNetwork( int numberOfNodes, int[][] edge, double[] edgeWeight, double[] nodeWeight )
    {
        this( numberOfNodes, edge, edgeWeight, nodeWeight, null, null );
    }

    public ReducedNetwork( int numberOfNodes, int[][] edge, double[] edgeWeight, double[] nodeWeight, int[] cluster,
            Map<Integer,Node> nodesMap )
    {
        this.nodesMap = nodesMap;
        if ( nodesMap == null )
        {
            this.nodesMap = new HashMap<>();
        }


        double[] edgeWeight2;
        int i, j, nEdges, nEdgesWithoutSelfLinks;
        int[] neighbor;

        this.numberOfNodes = numberOfNodes;

        nEdges = edge[0].length;
        firstNeighborIndex = new int[numberOfNodes + 1];
        if ( edgeWeight == null )
        {
            edgeWeight = new double[nEdges];
            for ( i = 0; i < nEdges; i++ )
            { edgeWeight[i] = 1; }
        }
        totalEdgeWeightSelfLinks = 0;

        neighbor = new int[nEdges];
        edgeWeight2 = new double[nEdges];
        i = 1;
        nEdgesWithoutSelfLinks = 0;
        for ( j = 0; j < nEdges; j++ )
        {
            if ( edge[0][j] == edge[1][j] )
            { totalEdgeWeightSelfLinks += edgeWeight[j]; }
            else
            {
                if ( edge[0][j] >= i )
                {
                    for (; i <= edge[0][j]; i++ )
                    { firstNeighborIndex[i] = nEdgesWithoutSelfLinks; }
                }
                neighbor[nEdgesWithoutSelfLinks] = edge[1][j];
                edgeWeight2[nEdgesWithoutSelfLinks] = edgeWeight[j];
                nEdgesWithoutSelfLinks++;
            }
        }
        for (; i <= numberOfNodes; i++ )
        { firstNeighborIndex[i] = nEdgesWithoutSelfLinks; }

        this.neighbor = new int[nEdgesWithoutSelfLinks];
        System.arraycopy( neighbor, 0, this.neighbor, 0, nEdgesWithoutSelfLinks );
        this.edgeWeight = new double[nEdgesWithoutSelfLinks];
        System.arraycopy( edgeWeight2, 0, this.edgeWeight, 0, nEdgesWithoutSelfLinks );

        if ( nodeWeight == null )
        {
            this.nodeWeight = new double[numberOfNodes];
            for ( i = 0; i < numberOfNodes; i++ )
            { this.nodeWeight[i] = 1; }
        }
        else
        { this.nodeWeight = nodeWeight; }

        setClusters( cluster );
    }

    public ReducedNetwork( int numberOfNodes, int[] firstNeighborIndex, int[] neighbor )
    {
        this( numberOfNodes, firstNeighborIndex, neighbor, null, null, null );
    }

    public ReducedNetwork( int numberOfNodes, int[] firstNeighborIndex, int[] neighbor, double[] edgeWeight )
    {
        this( numberOfNodes, firstNeighborIndex, neighbor, edgeWeight, null, null );
    }

    public ReducedNetwork( int numberOfNodes, int[] firstNeighborIndex, int[] neighbor, double[] edgeWeight,
            double[] nodeWeight, Map<Integer,Node> nodesMap )
    {
        this( numberOfNodes, firstNeighborIndex, neighbor, edgeWeight, nodeWeight, null, nodesMap );
    }

    public ReducedNetwork( int numberOfNodes, int[] firstNeighborIndex, int[] neighbor, double[] edgeWeight,
            double[] nodeWeight, int[] cluster, Map<Integer,Node> nodesMap )
    {
        this.nodesMap = nodesMap;

        int i, nEdges;

        this.numberOfNodes = numberOfNodes;

        this.firstNeighborIndex = firstNeighborIndex;
        this.neighbor = neighbor;

        if ( edgeWeight == null )
        {
            nEdges = neighbor.length;
            this.edgeWeight = new double[nEdges];
            for ( i = 0; i < nEdges; i++ )
            { this.edgeWeight[i] = 1; }
        }
        else
        { this.edgeWeight = edgeWeight; }

        if ( nodeWeight == null )
        {
            this.nodeWeight = new double[numberOfNodes];
            for ( i = 0; i < numberOfNodes; i++ )
            { this.nodeWeight[i] = 1; }
        }
        else
        { this.nodeWeight = nodeWeight; }

        setClusters( cluster );
    }


    public static ReducedNetwork create( String fileName, ModularityOptimizer.ModularityFunction modularityFunction )
            throws IOException
    {
        double[] edgeWeight;
        int i, nEdges;
        int[] neighbor;

        List<Relationship> relationships = new ArrayList<>( 10_000 );
        Map<Integer,Node> nodesMap = new TreeMap<Integer,Node>(  )
        {
        };
        try ( BufferedReader bufferedReader = new BufferedReader( new FileReader( fileName ) ) )
        {
            String line;
            while ( (line = bufferedReader.readLine()) != null )
            {
                String[] parts = line.split( "\t" );
                int sourceId = parseInt( parts[0] );
                int destinationId = parseInt( parts[1] );

                Node source = nodesMap.get( sourceId );
                if ( source == null )
                {
                    source = new Node( sourceId );
                    nodesMap.put( sourceId, source );
                }

                Node destination = nodesMap.get( destinationId );
                if ( destination == null )
                {
                    destination = new Node( destinationId );
                    nodesMap.put( destinationId, destination );
                }
                double weight = (parts.length > 2) ? parseDouble( parts[2] ) : 1;
                destination.in( source, weight );
                source.out( destination, weight );

                relationships.add( Relationship.from( line ) );
            }
        }

        int numberOfNodes = nodesMap.size();
        int[] degree = degree( nodesMap );
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

        return modularityFunction.createReducedNetwork( numberOfNodes, nEdges, firstNeighborIndex, neighbor, edgeWeight,
                nodesMap );
    }

    private static int[] degree( Map<Integer,Node> nodesMap )
    {
        int[] numberOfNeighbours = new int[nodesMap.size()];

        for ( Map.Entry<Integer,Node> entry : nodesMap.entrySet() )
        {
            numberOfNeighbours[entry.getKey()] = entry.getValue().degree();
        }

        return numberOfNeighbours;
    }

    public Object clone()
    {
        ReducedNetwork clonedNetwork;

        try
        {
            clonedNetwork = (ReducedNetwork) super.clone();

            if ( cluster != null )
            { clonedNetwork.cluster = (int[]) cluster.clone(); }
            clonedNetwork.deleteClusteringStats();

            return clonedNetwork;
        }
        catch ( CloneNotSupportedException e )
        {
            return null;
        }
    }

    public void save( String fileName ) throws IOException
    {
        ObjectOutputStream objectOutputStream;

        objectOutputStream = new ObjectOutputStream( new FileOutputStream( fileName ) );

        objectOutputStream.writeObject( this );

        objectOutputStream.close();
    }

    public int getNNodes()
    {
        return numberOfNodes;
    }

    public int getNEdges()
    {
        return neighbor.length;
    }

    public int[][] getEdges()
    {
        int[][] edge;
        int i, j;

        edge = new int[2][neighbor.length];
        for ( i = 0; i < numberOfNodes; i++ )
        {
            for ( j = firstNeighborIndex[i]; j < firstNeighborIndex[i + 1]; j++ )
            {
                edge[0][j] = i;
                edge[1][j] = neighbor[j];
            }
        }

        return edge;
    }

    public double getTotalEdgeWeight()
    {
        double totalEdgeWeight;
        int i;

        totalEdgeWeight = totalEdgeWeightSelfLinks;
        for ( i = 0; i < neighbor.length; i++ )
        {
            totalEdgeWeight += edgeWeight[i];
        }

        return totalEdgeWeight;
    }

    public double[] getEdgeWeights()
    {
        return edgeWeight;
    }

    public double getTotalNodeWeight()
    {
        double totalNodeWeight;
        int i;

        totalNodeWeight = 0;
        for ( i = 0; i < numberOfNodes; i++ )
        { totalNodeWeight += nodeWeight(i); }

        return totalNodeWeight;
    }

    public double[] getNodeWeights()
    {
        return nodeWeight;
    }

    public int getNClusters()
    {
        return numberOfClusters;
    }

    public int[] getClusters()
    {
        return cluster;
    }

    public double[] getClusterWeights()
    {
        if ( cluster == null )
        { return null; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        return clusterWeight;
    }

    public int[] getNNodesPerCluster()
    {
        if ( cluster == null )
        { return null; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        return numberNodesPerCluster;
    }

    public int[][] getNodesPerCluster()
    {
        if ( cluster == null )
        { return null; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        return nodePerCluster;
    }

    public void setClusters( int[] cluster )
    {
        numberOfClusters = Clusters.calculateNumberOfClusters( cluster, numberOfNodes );
        this.cluster = cluster;
        deleteClusteringStats();
    }

    public void initSingletonClusters()
    {
        int i;

        numberOfClusters = numberOfNodes;
        cluster = new int[numberOfNodes];
        for ( i = 0; i < numberOfNodes; i++ )
        {
            cluster[i] = i;
        }

        deleteClusteringStats();
    }

    public void findConnectedComponents()
    {
        int i, j;
        int[] neighborIndex, node;

        cluster = new int[numberOfNodes];
        init();

        node = new int[numberOfNodes];
        neighborIndex = new int[numberOfNodes];

        numberOfClusters = 0;
        for ( i = 0; i < numberOfNodes; i++ )
        {
            if ( cluster[i] == -1 )
            {
                cluster[i] = numberOfClusters;
                node[0] = i;
                neighborIndex[0] = firstNeighborIndex[i];
                j = 0;
                do
                {
                    if ( neighborIndex[j] == firstNeighborIndex[node[j] + 1] )
                    { j--; }
                    else if ( cluster[neighbor[neighborIndex[j]]] == -1 )
                    {
                        cluster[neighbor[neighborIndex[j]]] = numberOfClusters;
                        node[j + 1] = neighbor[neighborIndex[j]];
                        neighborIndex[j + 1] = firstNeighborIndex[node[j + 1]];
                        neighborIndex[j]++;
                        j++;
                    }
                    else
                    { neighborIndex[j]++; }
                }
                while ( j >= 0 );

                numberOfClusters++;
            }
        }

        deleteClusteringStats();
    }

    private void init()
    {
        int i;
        for ( i = 0; i < numberOfNodes; i++ )
        { cluster[i] = -1; }
    }

    private static void printCluster( int[] clusters )
    {
        for ( int item : clusters )
        {
            System.out.print( item + " " );
        }
        System.out.println();
    }

    public void mergeClusters( int[] newCluster )
    {
        if ( cluster == null )
        {
            return;
        }

        int i = 0;
        for ( int j = 0; j < numberOfNodes; j++ )
        {
            int k = newCluster[cluster[j]];
            if ( k > i )
            {
                i = k;
            }
            cluster[j] = k;
        }
        numberOfClusters = i + 1;

        deleteClusteringStats();
    }

    public boolean removeCluster( int cluster )
    {
        boolean removed;

        if ( this.cluster == null )
        { return false; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        removed = removeCluster2( cluster );

        deleteClusteringStats();

        return removed;
    }

    public void removeSmallClusters( double minClusterWeight )
    {
        boolean[] ignore;
        double minClusterWeight2;
        int i, smallestCluster;
        ReducedNetwork reducedNetwork;

        if ( cluster == null )
        { return; }

        reducedNetwork = calculateReducedNetwork();
        reducedNetwork.initSingletonClusters();
        reducedNetwork.calcClusteringStats();

        ignore = new boolean[numberOfClusters];
        do
        {
            smallestCluster = -1;
            minClusterWeight2 = minClusterWeight;
            for ( i = 0; i < reducedNetwork.numberOfClusters; i++ )
            {
                if ( (!ignore[i]) && (reducedNetwork.clusterWeight[i] < minClusterWeight2) )
                {
                    smallestCluster = i;
                    minClusterWeight2 = reducedNetwork.clusterWeight[i];
                }
            }

            if ( smallestCluster >= 0 )
            {
                reducedNetwork.removeCluster2( smallestCluster );
                ignore[smallestCluster] = true;
            }
        }
        while ( smallestCluster >= 0 );

        mergeClusters( reducedNetwork.getClusters() );
    }

    public void orderClustersByWeight()
    {
        orderClusters( true );
    }

    public void orderClustersByNNodes()
    {
        orderClusters( false );
    }

    public ReducedNetwork createSubnetwork( int clusterId )
    {
        double[] subnetworkEdgeWeight;
        int[] subnetworkNeighbor, subnetworkNode;

        if ( this.cluster == null )
        {
            return null;
        }

        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        subnetworkNode = new int[numberOfNodes];
        subnetworkNeighbor = new int[neighbor.length];
        subnetworkEdgeWeight = new double[edgeWeight.length];

        return createSubnetwork( clusterId, subnetworkNode, subnetworkNeighbor, subnetworkEdgeWeight );
    }

    public ReducedNetwork[] createSubnetworks()
    {
        if ( cluster == null )
        {
            return null;
        }

        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        ReducedNetwork[] subnetwork = new ReducedNetwork[numberOfClusters];
        int[] subnetworkNode = new int[numberOfNodes];
        int[] subnetworkNeighbor = new int[neighbor.length];
        double[] subnetworkEdgeWeight = new double[edgeWeight.length];

        for ( int clusterId = 0; clusterId < numberOfClusters; clusterId++ )
        {
            subnetwork[clusterId] =
                    createSubnetwork( clusterId, subnetworkNode, subnetworkNeighbor, subnetworkEdgeWeight );
        }

        return subnetwork;
    }

    public ReducedNetwork calculateReducedNetwork()
    {
        double[] reducedNetworkEdgeWeight1, reducedNetworkEdgeWeight2;
        int i, j, k, l, m, reducedNetworkNEdges1, reducedNetworkNEdges2;
        int[] reducedNetworkNeighbor1, reducedNetworkNeighbor2;
        ReducedNetwork reducedNetwork;

        if ( cluster == null )
        {
            return null;
        }

        if ( !clusteringStatsAvailable )
        {
            calcClusteringStats();
        }

        reducedNetwork = new ReducedNetwork();

        reducedNetwork.numberOfNodes = numberOfClusters;
        reducedNetwork.firstNeighborIndex = new int[numberOfClusters + 1];
        reducedNetwork.totalEdgeWeightSelfLinks = totalEdgeWeightSelfLinks;
        reducedNetwork.nodeWeight = new double[numberOfClusters];

        reducedNetworkNeighbor1 = new int[neighbor.length];
        reducedNetworkEdgeWeight1 = new double[edgeWeight.length];

        reducedNetworkNeighbor2 = new int[numberOfClusters - 1];
        reducedNetworkEdgeWeight2 = new double[numberOfClusters];

        reducedNetworkNEdges1 = 0;
        for ( i = 0; i < numberOfClusters; i++ )
        {
            reducedNetworkNEdges2 = 0;
            for ( j = 0; j < nodePerCluster[i].length; j++ )
            {
                k = nodePerCluster[i][j];

                for ( l = firstNeighborIndex[k]; l < firstNeighborIndex[k + 1]; l++ )
                {
                    m = cluster[neighbor[l]];
                    if ( m != i )
                    {
                        if ( reducedNetworkEdgeWeight2[m] == 0 )
                        {
                            reducedNetworkNeighbor2[reducedNetworkNEdges2] = m;
                            reducedNetworkNEdges2++;
                        }
                        reducedNetworkEdgeWeight2[m] += edgeWeight[l];
                    }
                    else
                    {
                        reducedNetwork.totalEdgeWeightSelfLinks += edgeWeight[l];
                    }
                }

                reducedNetwork.nodeWeight[i] += nodeWeight(k);
            }

            for ( j = 0; j < reducedNetworkNEdges2; j++ )
            {
                reducedNetworkNeighbor1[reducedNetworkNEdges1 + j] = reducedNetworkNeighbor2[j];
                reducedNetworkEdgeWeight1[reducedNetworkNEdges1 + j] =
                        reducedNetworkEdgeWeight2[reducedNetworkNeighbor2[j]];
                reducedNetworkEdgeWeight2[reducedNetworkNeighbor2[j]] = 0;
            }
            reducedNetworkNEdges1 += reducedNetworkNEdges2;

            reducedNetwork.firstNeighborIndex[i + 1] = reducedNetworkNEdges1;
        }

        reducedNetwork.neighbor = new int[reducedNetworkNEdges1];
        reducedNetwork.edgeWeight = new double[reducedNetworkNEdges1];
        System.arraycopy( reducedNetworkNeighbor1, 0, reducedNetwork.neighbor, 0, reducedNetworkNEdges1 );
        System.arraycopy( reducedNetworkEdgeWeight1, 0, reducedNetwork.edgeWeight, 0, reducedNetworkNEdges1 );

        return reducedNetwork;
    }

    public ReducedNetwork getLargestConnectedComponent()
    {
        double maxClusterWeight;
        int i, largestCluster;
        ReducedNetwork clonedNetwork;

        clonedNetwork = (ReducedNetwork) clone();

        clonedNetwork.findConnectedComponents();

        clonedNetwork.calcClusteringStats();
        largestCluster = -1;
        maxClusterWeight = -1;
        for ( i = 0; i < clonedNetwork.numberOfClusters; i++ )
        {
            if ( clonedNetwork.clusterWeight[i] > maxClusterWeight )
            {
                largestCluster = i;
                maxClusterWeight = clonedNetwork.clusterWeight[i];
            }
        }

        return clonedNetwork.createSubnetwork( largestCluster );
    }

    public double calcQualityFunction( double resolution )
    {
        double qualityFunction, totalEdgeWeight;
        int i, j, k;

        if ( cluster == null )
        { return Double.NaN; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        qualityFunction = totalEdgeWeightSelfLinks;
        totalEdgeWeight = totalEdgeWeightSelfLinks;
        for ( i = 0; i < numberOfNodes; i++ )
        {
            j = cluster[i];
            for ( k = firstNeighborIndex[i]; k < firstNeighborIndex[i + 1]; k++ )
            {
                if ( cluster[neighbor[k]] == j )
                { qualityFunction += edgeWeight[k]; }
                totalEdgeWeight += edgeWeight[k];
            }
        }

        for ( i = 0; i < numberOfClusters; i++ )
        { qualityFunction -= clusterWeight[i] * clusterWeight[i] * resolution; }

        qualityFunction /= totalEdgeWeight;

        return qualityFunction;
    }

    public boolean runLocalMovingAlgorithm( double resolution )
    {
        return runLocalMovingAlgorithm( resolution, new Random() );
    }

    public boolean runLocalMovingAlgorithm( double resolution, Random random )
    {
        double qualityFunction;
        int[] newCluster;

        if ( (cluster == null) || (numberOfNodes == 1) )
        {
            return false;
        }

        boolean update = false;

        Map<Integer, Cluster> clusters = new HashMap<>(  );
        for ( int i = 0; i < numberOfNodes; i++ )
        {
            int clusterId = this.cluster[i];
            Cluster cluster = clusters.get( clusterId );
            if ( cluster == null )
            {
                cluster = new Cluster( clusterId );
                clusters.put( clusterId, cluster );
            }
            cluster.addWeight(nodeWeight[i]);
        }

        int[] numberOfNodesPerCluster = calculateNumberOfNodesPerCluster( numberOfNodes, cluster );

        int numberUnusedClusters = 0;
        int[] unusedCluster = new int[numberOfNodes];
        for ( int i = 0; i < numberOfNodes; i++ )
        {
            if ( numberOfNodesPerCluster[i] == 0 )
            {
                unusedCluster[numberUnusedClusters] = i;
                numberUnusedClusters++;
            }
        }

        int[] nodesInRandomOrder = nodesInRandomOrder( numberOfNodes, random );
        double[] edgeWeightsPointingToCluster = new double[numberOfNodes];
        int[] neighboringCluster = new int[numberOfNodes - 1];

        int numberStableNodes = 0;
        int i = 0;
        do
        {
            int nodeId = nodesInRandomOrder[i];

            int numberOfNeighbouringClusters = 0;
            for ( int k = firstNeighborIndex[nodeId]; k < firstNeighborIndex[nodeId + 1]; k++ )
            {
                int neighbourClusterId = cluster[neighbor[k]];
                if ( edgeWeightsPointingToCluster[neighbourClusterId] == 0 )
                {
                    neighboringCluster[numberOfNeighbouringClusters] = neighbourClusterId;
                    numberOfNeighbouringClusters++;
                }
                edgeWeightsPointingToCluster[neighbourClusterId] += edgeWeight[k];
            }

            clusters.get( cluster[nodeId] ).removeWeight( nodeWeight( nodeId ) );
            numberOfNodesPerCluster[cluster[nodeId]]--;
            if ( numberOfNodesPerCluster[cluster[nodeId]] == 0 )
            {
                unusedCluster[numberUnusedClusters] = cluster[nodeId];
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
			
			try 
            {
                System.out.println(clusters.get( bestCluster ));// NUll
                if(null == clusters.get( bestCluster ))
				{
                    return update;
                }
            }
            catch (NullPointerException e) { 
                System.out.println("NullPointerException Description:"+e);
                throw e;
            }
            catch (Exception e) 
            {
                System.out.println("Exception occurred");
                throw e;
            }

			
            clusters.get( bestCluster ).addWeight( nodeWeight( nodeId ) );
            numberOfNodesPerCluster[bestCluster]++;
            if ( bestCluster == cluster[nodeId] )
            {
                numberStableNodes++;
            }
            else
            {
//                updateCluster(nodeId, bestCluster);
                cluster[nodeId] = bestCluster;

//                if ( nodesMap != null )
//                {
//                    Node node = nodesMap.get( nodeId );
//                    if(node == null) {
////                        System.out.println( "nodeId = " + nodeId + " => " + nodesMap );
//                    } else {
//                        node.setCluster( bestCluster );
//                    }
//                }

                numberStableNodes = 1;
                update = true;
            }

            i = (i < numberOfNodes - 1) ? (i + 1) : 0;
        }
        while ( numberStableNodes < numberOfNodes );

        newCluster = new int[numberOfNodes];
        numberOfClusters = 0;
        for ( i = 0; i < numberOfNodes; i++ )
        {
            if ( numberOfNodesPerCluster[i] > 0 )
            {
                newCluster[i] = numberOfClusters;
                numberOfClusters++;
            }
        }
        for ( i = 0; i < numberOfNodes; i++ )
        {
            cluster[i] = newCluster[cluster[i]];
        }

        deleteClusteringStats();

        return update;
    }

    private double nodeWeight( int nodeId )
    {
        return nodeWeight[nodeId];
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

    private int[] calculateNumberOfNodesPerCluster( int numberOfNodes, int[] cluster )
    {
        int[] numberOfNodesPerCluster = new int[numberOfNodes];
        for ( int i = 0; i < numberOfNodes; i++ )
        {
            numberOfNodesPerCluster[cluster[i]]++;
        }
        return numberOfNodesPerCluster;
    }

    public boolean runLouvainAlgorithm( double resolution )
    {
        return runLouvainAlgorithm( resolution, new Random() );
    }

    public boolean runLouvainAlgorithm( double resolution, Random random )
    {
        boolean update, update2;
        ReducedNetwork reducedNetwork;

        if ( (cluster == null) || (numberOfNodes == 1) )
        { return false; }

        update = runLocalMovingAlgorithm( resolution, random );

        if ( numberOfClusters < numberOfNodes )
        {
            reducedNetwork = calculateReducedNetwork();
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

    public boolean runLouvainAlgorithmWithMultilevelRefinement( double resolution )
    {
        return runLouvainAlgorithmWithMultilevelRefinement( resolution, new Random() );
    }

    public boolean runLouvainAlgorithmWithMultilevelRefinement( double resolution, Random random )
    {
        boolean update, update2;
        ReducedNetwork reducedNetwork;

        if ( (cluster == null) || (numberOfNodes == 1) )
        { return false; }

        update = runLocalMovingAlgorithm( resolution, random );

        if ( numberOfClusters < numberOfNodes )
        {
            reducedNetwork = calculateReducedNetwork();
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

    public boolean runSmartLocalMovingAlgorithm( double resolution )
    {
        return runSmartLocalMovingAlgorithm( resolution, new Random() );
    }

    public boolean runSmartLocalMovingAlgorithm( double resolution, Random random )
    {
        if ( (cluster == null) || (numberOfNodes == 1) )
        {
            return false;
        }

        boolean update = runLocalMovingAlgorithm( resolution, random );
        // after this cluster is updated with the 8 clusters

        if ( numberOfClusters < numberOfNodes )
        {
            if ( !clusteringStatsAvailable )
            {
                calcClusteringStats();
            }

            ReducedNetwork[] subnetworks = createSubnetworks();

            numberOfClusters = 0;
            for ( int subnetworkId = 0; subnetworkId < subnetworks.length; subnetworkId++ )
            {
                // need to add the nodes map to the sub network
                ReducedNetwork subnetwork = subnetworks[subnetworkId];

                // this currently isn't being set on a reduced network
                // that seems to behave differently though as I don't think the network represents actual nodes, but
                // rather groups of them
                if ( nodesMap != null )
                {
                    Map<Integer,Node> newNodesMap = new HashMap<>();
                    for ( int nodeId : nodePerCluster[subnetworkId] )
                    {
                        Node node = nodesMap.get( nodeId );
                        newNodesMap.put( nodeId, node );
                    }
                    subnetwork.nodesMap = newNodesMap;
                }

                subnetwork.initSingletonClusters();
                subnetwork.runLocalMovingAlgorithm( resolution, random );

                int[] subnetworkCluster = subnetwork.getClusters();
                for ( int nodeId = 0; nodeId < subnetworkCluster.length; nodeId++ )
                {
                    cluster[nodePerCluster[subnetworkId][nodeId]] = numberOfClusters + subnetworkCluster[nodeId];
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
//            printCluster( cluster );
        }

        deleteClusteringStats();

        return update;
    }

    public  ReducedNetwork()
    {
    }

    private void writeObject( ObjectOutputStream out ) throws IOException
    {
        deleteClusteringStats();

        out.defaultWriteObject();
    }

    boolean removeCluster2( int cluster )
    {
        double maxQualityFunction, qualityFunction;
        double[] reducedNetworkEdgeWeight;
        int bestCluster, i, j;

        reducedNetworkEdgeWeight = new double[numberOfClusters];
        for ( i = 0; i < numberOfNodes; i++ )
        {
            if ( this.cluster[i] == cluster )
            {
                for ( j = firstNeighborIndex[i]; j < firstNeighborIndex[i + 1]; j++ )
                { reducedNetworkEdgeWeight[this.cluster[neighbor[j]]] += edgeWeight[j]; }
            }
        }

        bestCluster = -1;
        maxQualityFunction = 0;
        for ( i = 0; i < numberOfClusters; i++ )
        {
            if ( (i != cluster) && (clusterWeight[i] > 0) )
            {
                qualityFunction = reducedNetworkEdgeWeight[i] / clusterWeight[i];
                if ( qualityFunction > maxQualityFunction )
                {
                    bestCluster = i;
                    maxQualityFunction = qualityFunction;
                }
            }
        }

        if ( bestCluster == -1 )
        { return false; }

        for ( i = 0; i < numberOfNodes; i++ )
        {
            if ( this.cluster[i] == cluster )
            { this.cluster[i] = bestCluster; }
        }

        clusterWeight[bestCluster] += clusterWeight[cluster];
        clusterWeight[cluster] = 0;

        if ( cluster == numberOfClusters - 1 )
        {
            i = 0;
            for ( j = 0; j < numberOfNodes; j++ )
            {
                if ( this.cluster[j] > i )
                { i = this.cluster[j]; }
            }
            numberOfClusters = i + 1;
        }

        return true;
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

        if ( cluster == null )
        { return; }

        if ( !clusteringStatsAvailable )
        { calcClusteringStats(); }

        clusterSize = new ClusterSize[numberOfClusters];
        for ( i = 0; i < numberOfClusters; i++ )
        { clusterSize[i] = new ClusterSize( i, orderByWeight ? clusterWeight[i] : numberNodesPerCluster[i] ); }

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
        for ( i = 0; i < numberOfNodes; i++ )
        { cluster[i] = newCluster[cluster[i]]; }

        deleteClusteringStats();
    }

    private ReducedNetwork createSubnetwork( int clusterId, int[] subnetworkNode, int[] subnetworkNeighbor,
            double[] subnetworkEdgeWeight )
    {
        int k;

        ReducedNetwork subnetwork = new ReducedNetwork();

        int numberOfNodesInSubnetwork = nodePerCluster[clusterId].length;
        subnetwork.numberOfNodes = numberOfNodesInSubnetwork;

        if ( numberOfNodesInSubnetwork == 1 )
        {
            subnetwork.firstNeighborIndex = new int[2];
            subnetwork.neighbor = new int[0];
            subnetwork.edgeWeight = new double[0];
            subnetwork.nodeWeight = new double[]{nodeWeight(nodePerCluster[clusterId][0])};
        }
        else
        {
            // creating a mapping from the top level Network node ids to our local sub network node ids
            for ( int i = 0; i < nodePerCluster[clusterId].length; i++ )
            {
                subnetworkNode[nodePerCluster[clusterId][i]] = i;
            }

            subnetwork.firstNeighborIndex = new int[numberOfNodesInSubnetwork + 1];
            subnetwork.nodeWeight = new double[numberOfNodesInSubnetwork];

            int subnetworkNEdges = 0;
            for ( int i = 0; i < numberOfNodesInSubnetwork; i++ )
            {
                int nodeId = nodePerCluster[clusterId][i];

                // iterate all the neighbouring nodes of 'nodeId'
                // firstNeighborIndex[nodeId] gives us this node
                // firstNeighbor[nodeId +1] gives us the first neighbour of the next node
                for ( k = firstNeighborIndex[nodeId]; k < firstNeighborIndex[nodeId + 1]; k++ )
                {
                    if ( this.cluster[neighbor[k]] == clusterId )
                    {
                        subnetworkNeighbor[subnetworkNEdges] = subnetworkNode[neighbor[k]];
                        subnetworkEdgeWeight[subnetworkNEdges] = edgeWeight[k];
                        subnetworkNEdges++;
                    }
                }

                subnetwork.firstNeighborIndex[i + 1] = subnetworkNEdges;
                subnetwork.nodeWeight[i] = nodeWeight(nodeId);
            }


            subnetwork.neighbor = new int[subnetworkNEdges];
            subnetwork.edgeWeight = new double[subnetworkNEdges];
            System.arraycopy( subnetworkNeighbor, 0, subnetwork.neighbor, 0, subnetworkNEdges );
            System.arraycopy( subnetworkEdgeWeight, 0, subnetwork.edgeWeight, 0, subnetworkNEdges );
        }

        subnetwork.totalEdgeWeightSelfLinks = 0;

        return subnetwork;
    }

    void calcClusteringStats()
    {
        int i, j;

        clusterWeight = new double[numberOfClusters];
        numberNodesPerCluster = new int[numberOfClusters];
        nodePerCluster = new int[numberOfClusters][];

        for ( i = 0; i < numberOfNodes; i++ )
        {
            clusterWeight[cluster[i]] += nodeWeight(i);
//            clusterWeight[cluster[i]] += nodesMap.get( i ).weight();
            numberNodesPerCluster[cluster[i]]++;
        }

        for ( i = 0; i < numberOfClusters; i++ )
        {
            nodePerCluster[i] = new int[numberNodesPerCluster[i]];
            numberNodesPerCluster[i] = 0;
        }

        for ( i = 0; i < numberOfNodes; i++ )
        {
            j = cluster[i];
            nodePerCluster[j][numberNodesPerCluster[j]] = i;
            numberNodesPerCluster[j]++;
        }

        clusteringStatsAvailable = true;
    }

    private void deleteClusteringStats()
    {
        clusterWeight = null;
        numberNodesPerCluster = null;
        nodePerCluster = null;

        clusteringStatsAvailable = false;
    }

    private class Cluster
    {
        private int clusterId;
        private double weight = 0.0;

        public Cluster( int clusterId )
        {

            this.clusterId = clusterId;
        }

        public void addWeight( double weight )
        {

            this.weight += weight;
        }

        public void removeWeight( double weight )
        {
            this.weight -= weight;
        }
    }
}

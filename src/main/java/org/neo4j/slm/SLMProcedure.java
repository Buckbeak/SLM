package org.neo4j.slm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.UUID;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Relationship;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.PerformsWrites;
import org.neo4j.procedure.Procedure;

import static java.lang.String.format;

import static org.neo4j.graphdb.Direction.OUTGOING;
import static org.neo4j.graphdb.RelationshipType.withName;

public class SLMProcedure
{
    @Context
    public org.neo4j.graphdb.GraphDatabaseService db;

    //@Procedure( name = "org.neo4j.slm.subslm")
    @Procedure
    @PerformsWrites
    public Stream<Cluster> slm( @Name("label") String label, @Name("relationshipType") String relationshipType,@Name("hostname_json") String hostname_json) throws IOException
    {
//        try ( Transaction tx = db.beginTx() )
//        {
//            try(ResourceIterator<org.neo4j.graphdb.Node> nodesByLabel = db.findNodes( Label.label( label ) ))
//            {
//                while ( nodesByLabel.hasNext() )
//                {
//                    org.neo4j.graphdb.Node next = nodesByLabel.next();
//
//                    for ( Relationship relationship : next.getRelationships( withName( relationshipType ), OUTGOING ) )
//                    {
//                        relationship.getEndNode().hasLabel( Label.label( label ) );
//                    }
//                }
//            }
//
//        }

        String query =  "MATCH (person1:" + label +")-[r:" + relationshipType + "]-(person2:" + label + ") \n" +
                        "WHERE person1.hostname IN " + hostname_json + " \n" +
                        "AND \n" +
                        "person2.hostname IN " + hostname_json + " \n" +
                        "RETURN person1.id AS p1, person2.id AS p2, toFloat(1) AS weight";

        Result rows = db.execute( query );

        System.out.println(rows.toString());

        ModularityOptimizer.ModularityFunction modularityFunction = ModularityOptimizer.ModularityFunction.Standard;
        Network network = Network.create( modularityFunction, rows );

        double resolution = 1.0;
        int nRandomStarts = 1;
        int nIterations = 10;
        long randomSeed = 0;

        double modularity;

        Random random = new Random( randomSeed );

        double resolution2 = modularityFunction.resolution( resolution, network );

        Map<Integer, Node> cluster = new HashMap<>();
        double maxModularity = Double.NEGATIVE_INFINITY;

        for ( int randomStart = 0; randomStart < nRandomStarts; randomStart++ )
        {
            network.initSingletonClusters();

            int iteration = 0;
            do
            {
                network.runSmartLocalMovingAlgorithm( resolution2, random );
                iteration++;

                modularity = network.calcQualityFunction( resolution2 );
            } while ( (iteration < nIterations) );

            if ( modularity > maxModularity )
            {
                network.orderClustersByNNodes();
                cluster = network.getNodes();
                maxModularity = modularity;
            }
        }

        for ( Map.Entry<Integer, Node> entry : cluster.entrySet() )
        {
            try ( Transaction tx = db.beginTx() )
            {
                org.neo4j.graphdb.Node node = db.findNode( Label.label( label ), "id", String.valueOf( entry.getKey() ) );

                //Try 1
                //node.addLabel( Label.label( (format( "Community%d", entry.getValue().getCluster() )) ) );

                //Try 2
/*
                if(labelExists(Object("Community%d")))
                {
                    node.addLabel( Label.label( (format( "ClusterCommunity%d", entry.getValue().getCluster() )) ) );
                }
                else
                {
                    //String uniqueID = UUID.randomUUID().toString();
                    //node.addLabel( Label.label( (format( uniqueID, entry.getValue().getCluster() )) ) );
                }
*/

                //Try 3
/*
                if(node.getProperty("id"))
                {
                    node.getProperty();
                    System.out.println(node.getProperty("id").toString());
                    node.setProperty("Cluster",entry.getValue().getCluster());
                    node.setProperty("Cluster",entry.getValue().getCluster());
                }
*/

                //Try 4
                    node.setProperty("Cluster",entry.getValue().getCluster());

                tx.success();
            }
        }

        return cluster
                .entrySet()
                .stream()
                .map( ( entry ) -> new Cluster( entry.getKey(), entry.getValue().getCluster() ) );
    }

        //@Procedure( name = "org.neo4j.slm.subslm")
    @Procedure
    @PerformsWrites
    public Stream<Cluster> subSlm( @Name("label") String label, @Name("relationshipType") String relationshipType, @Name("constraint,0") String constraint) throws IOException
    {

        String query = "MATCH (s1:" + label + " {Cluster:"+constraint+"})-[r:" + relationshipType + "]-(s2:" + label + " {Cluster:"+constraint+"})\n" +
                        "RETURN s1.id AS p1, s2.id AS p2, toFloat(1) AS weight";

        Result rows = db.execute( query );

        System.out.println(rows.toString());

        ModularityOptimizer.ModularityFunction modularityFunction = ModularityOptimizer.ModularityFunction.Standard;
        Network network = Network.create( modularityFunction, rows );

        double resolution = 1.0;
        int nRandomStarts = 1;
        int nIterations = 10;
        long randomSeed = 0;

        double modularity;

        Random random = new Random( randomSeed );

        double resolution2 = modularityFunction.resolution( resolution, network );

        Map<Integer, Node> cluster = new HashMap<>();
        double maxModularity = Double.NEGATIVE_INFINITY;

        for ( int randomStart = 0; randomStart < nRandomStarts; randomStart++ )
        {
            network.initSingletonClusters();

            int iteration = 0;
            do
            {
                network.runSmartLocalMovingAlgorithm( resolution2, random );
                iteration++;

                modularity = network.calcQualityFunction( resolution2 );
            } while ( (iteration < nIterations) );

            if ( modularity > maxModularity )
            {
                network.orderClustersByNNodes();
                cluster = network.getNodes();
                maxModularity = modularity;
            }
        }

        for ( Map.Entry<Integer, Node> entry : cluster.entrySet() )
        {
            try ( Transaction tx = db.beginTx() )
            {
                org.neo4j.graphdb.Node node = db.findNode( Label.label( label ), "id", String.valueOf(entry.getKey()));
                    node.setProperty("SubCluster",entry.getValue().getCluster());
                tx.success();
            }
        }

        return cluster
                .entrySet()
                .stream()
                .map( ( entry ) -> new Cluster( entry.getKey(), entry.getValue().getCluster() ) );
    }

/*
    public static boolean labelExists(Object node)
    {
        label = DynamicLabel.label(node);
        Iterable<IndexDefinition> indexes = schema.getIndexes(label);
        for(IndexDefinition index : indexes) 
        {
            for (String key: index.getPropertyKeys()) 
            {
                if (key.equals("id")) 
                {
                    return true; // index for label and property exists
                }
            }
        }
        return false; // no matching schema index
    }
*/

    public static class Cluster
    {
        public long id;
        public long clusterId;

        public Cluster( int id, int clusterId )
        {
            this.id = id;
            this.clusterId = clusterId;
        }
    }
}

package org.intermine.bio.postprocess;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.bio.util.Constants;
import org.intermine.bio.util.PostProcessUtil;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.MetaDataException;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Chromosome;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.DataSource;
import org.intermine.model.bio.Intron;
import org.intermine.model.bio.Location;
import org.intermine.model.bio.Organism;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.intermine.objectstore.query.BagConstraint;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryObjectReference;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.postprocess.PostProcessor;
import org.intermine.util.DynamicUtil;


/**
 * Methods for creating feature for introns.
 */
public class CreateIntronFeaturesProcess extends PostProcessor
{

    private ObjectStore os;
    private DataSet dataSet;
    private DataSource dataSource;
    private Set<Integer> taxonIds = new HashSet<Integer>();
    private Model model;

    protected Map<String, SequenceFeature> intronMap = new HashMap<String, SequenceFeature>();
    protected Map<SequenceFeature, Set<SequenceFeature>> intronTranscripts =
            new HashMap<SequenceFeature, Set<SequenceFeature>>();
    private static final Logger LOG = Logger.getLogger(CreateIntronFeaturesProcess.class);

    /**
     * Create a new instance
     *
     * @param osw object store writer
     */
    public CreateIntronFeaturesProcess(ObjectStoreWriter osw) {
        super(osw);
        this.os = osw.getObjectStore();
        this.model = os.getModel();
        dataSource = (DataSource) DynamicUtil.createObject(Collections.singleton(DataSource.class));
        dataSource.setName("InterMine");
        try {
            dataSource = os.getObjectByExample(dataSource, Collections.singleton("name"));
        } catch (ObjectStoreException e) {
            throw new RuntimeException("unable to fetch IntermMine DataSource object", e);
        }
    }

    /**
     * Set a comma separated list of taxon ids to create introns for.  If no list
     * is provided introns will be created for all organisms.
     * @param organisms a comma separated list of taxon ids
     */
    public void setOrganisms(String organisms) {
        if (!StringUtils.isEmpty(organisms)) {
            String[] array = organisms.split(",");
            for (int i = 0; i < array.length; i++) {
                taxonIds.add(new Integer(array[i].trim()));
            }
        }
    }

    /**
     * {@inheritDoc}
     * <br/>
     * Main post-processing routine.
     * Create a new IntronUtil object that will operate on the given ObjectStoreWriter.
     * NOTE - needs to be run after SequenceFeature.chromosomeLocation has been set.
     *
     * @throws ObjectStoreException if the objectstore throws an exception
     */
    public void postProcess()
            throws ObjectStoreException {
        LOG.info("Start postprocessing ... create-intron-features-kai.");
        System.out.println("Start postprocessing ... create-intron-features-kai.");

        dataSet = (DataSet) DynamicUtil.createObject(Collections.singleton(DataSet.class));
        dataSet.setName("Calculated introns");
        dataSet.setDescription("Introns calculated by InterMine post-processing.");
        dataSet.setVersion("" + new Date()); // current time and date
        dataSet.setUrl("https://www.intermine.org");
        dataSet.setDataSource(dataSource);

        // Documented as an example of how to use the query API

        // This query finds all transcripts and their chromosome locations and exons
        // for each transcript with the exon chromosome location.  This is then used
        // to calculate intron locations.

        try {
            final String message = "Now performing create introns postprocess ";
            PostProcessUtil.checkFieldExists(model, "Transcript", "exons", message);
            PostProcessUtil.checkFieldExists(model, "Intron", "transcripts", message);
            PostProcessUtil.checkFieldExists(model, "Exon", null, message);
        } catch (MetaDataException e) {
            return;
        }

        // Construct a new query and a set to hold constraints that will be ANDed together
        Query q = new Query();
        ConstraintSet cs = new ConstraintSet(ConstraintOp.AND);

        // Add Gene to the from and select lists
        QueryClass qcGene = new QueryClass(model.getClassDescriptorByName("Gene").getType());
        q.addFrom(qcGene);
        q.addToSelect(qcGene);

        // Include the referenced chromosomeLocation of the Gene
        QueryClass qcGeneLoc = new QueryClass(Location.class);
        q.addFrom(qcGeneLoc);
        q.addToSelect(qcGeneLoc);
        QueryObjectReference qorTranLoc = new QueryObjectReference(qcGene, "chromosomeLocation");
        cs.addConstraint(new ContainsConstraint(qorTranLoc, ConstraintOp.CONTAINS, qcGeneLoc));

        // restict to taxonIds if specified
        if (!taxonIds.isEmpty()) {
            QueryClass qcOrg = new QueryClass(Organism.class);
            q.addFrom(qcOrg);
            QueryObjectReference orgRef = new QueryObjectReference(qcGene, "organism");
            cs.addConstraint(new ContainsConstraint(orgRef, ConstraintOp.CONTAINS, qcOrg));
            QueryField qfTaxonId = new QueryField(qcOrg, "taxonId");
            cs.addConstraint(new BagConstraint(qfTaxonId, ConstraintOp.IN, taxonIds));
        }

        // Include Transcript class from the Gene.transcripts collection
        QueryClass qcTran = new QueryClass(model.getClassDescriptorByName("Transcript").getType());
        q.addFrom(qcTran);
        QueryCollectionReference qcrTranscripts = new QueryCollectionReference(qcGene, "transcripts");
        cs.addConstraint(new ContainsConstraint(qcrTranscripts, ConstraintOp.CONTAINS, qcTran));

        // Include the Exon class from the Transcript.exons collection
        QueryClass qcExon = new QueryClass(model.getClassDescriptorByName("Exon").getType());
        q.addFrom(qcExon);
        QueryCollectionReference qcrExons = new QueryCollectionReference(qcTran, "exons");
        cs.addConstraint(new ContainsConstraint(qcrExons, ConstraintOp.CONTAINS, qcExon));

        // Include the referenced chromosomeLocation of each Exon
        QueryClass qcExonLoc = new QueryClass(Location.class);
        q.addFrom(qcExonLoc);
        q.addToSelect(qcExonLoc);
        QueryObjectReference qorExonLoc = new QueryObjectReference(qcExon, "chromosomeLocation");
        cs.addConstraint(new ContainsConstraint(qorExonLoc, ConstraintOp.CONTAINS, qcExonLoc));

        // Set the constraint of the query
        q.setConstraint(cs);

        // Force an order by transcripts to make processing easier
        q.addToOrderBy(qcGene);

        // Precompute this query first, this will create a precomputed table holding
        // all the results.  The will make all batches after the first faster to fetch
        ((ObjectStoreInterMineImpl) os).precompute(q, Constants.PRECOMPUTE_CATEGORY);

        // Set up the results, the query isn't actually executed until we begin
        // iterating through the results
        Results results = os.execute(q, 500, true, true, true);

        // When we start interating the query will be executed
        Iterator<?> resultsIter = results.iterator();

        Set<Location> locationSet = new HashSet<Location>();
        SequenceFeature lastGene = null;
        Location lastGeneLoc = null;
        int tranCount = 0, exonCount = 0, intronCount = 0;

        osw.beginTransaction();
        while (resultsIter.hasNext()) {
            // Results is a list of ResultsRows, each ResultsRow contains the objects/fields
            // that were added to the select list of the query.  The order of columns is
            // as they were added to the select list.
            ResultsRow<?> rr = (ResultsRow<?>) resultsIter.next();
            SequenceFeature thisGene = (SequenceFeature) rr.get(0);

            if (lastGene == null) {
                lastGene = thisGene;
                lastGeneLoc = (Location) rr.get(1);
            }

            if (!thisGene.getId().equals(lastGene.getId())) {
                tranCount++;
                intronCount += createIntronFeatures(locationSet, lastGene, lastGeneLoc);
                exonCount += locationSet.size();
                if ((tranCount % 1000) == 0) {
                    LOG.info("Created " + intronCount + " Introns for " + tranCount
                            + " Transcripts with " + exonCount + " Exons.");
                }
                locationSet = new HashSet<Location>();
                lastGene = thisGene;
                lastGeneLoc = (Location) rr.get(1);
            }
            locationSet.add((Location) rr.get(2));
        }

        if (lastGene != null) {
            intronCount += createIntronFeatures(locationSet, lastGene, lastGeneLoc);
            tranCount++;
            exonCount += locationSet.size();
        }

        LOG.info("Read " + tranCount + " transcripts with " + exonCount + " exons.");
        System.out.println("Read " + tranCount + " transcripts with " + exonCount + " exons.");

        //osw.beginTransaction();
        int stored = 0;
        for (Iterator<String> i = intronMap.keySet().iterator(); i.hasNext();) {
            String identifier = i.next();
            SequenceFeature intron = intronMap.get(identifier);
            Set<SequenceFeature> transcripts = intronTranscripts.get(intron);
            if (transcripts != null) {
                intron.setFieldValue("transcripts", transcripts);
            }
            osw.store(intron);
            stored++;
            if (stored % 1000 == 0) {
                LOG.info("Stored " + stored + " introns.");
            }
        }
        LOG.info("Totally " + stored + " introns stored.");
        System.out.println("Totally " + stored + " introns stored.");

        if (intronMap.size() > 1) {
            osw.store(dataSet);
        }
        osw.commitTransaction();
    }

    /**
     * Return the number of created Intron objects that don't overlap the Locations
     * in the locationSet argument.  The caller must call ObjectStoreWriter.store() on the
     * Intron, its chromosomeLocation and the synonym in the synonyms collection.
     * @param locationSet a set of Locations for the exons on a particular transcript
     * @param gene Gene that the Locations refer to
     * @param geneLoc The Location of the Gene
     * @return the number of created Intron objects
     * @throws ObjectStoreException if there is an ObjectStore problem
     */
    protected int createIntronFeatures(Set<Location> locationSet, SequenceFeature gene,
                                       Location geneLoc)
            throws ObjectStoreException {
        if (locationSet.size() == 1 || geneLoc == null || gene == null
                || gene.getLength() == null) {
            return 0;
        }

        int geneLength = gene.getLength().intValue();
        final BitSet bs = new BitSet(geneLength);
        Chromosome chr = gene.getChromosome();

        int geneStart = geneLoc.getStart().intValue();

        for (Location location : locationSet) {
            String symbol = location.getLocatedOn().getSymbol();
            if (!chr.getSymbol().equals(symbol)) {
                continue;
            }

            int start = location.getStart().intValue() - geneStart;
            int end = location.getEnd().intValue() - geneStart;
            if (start < 0 || end < 0) {
                String msg = String.format("Invalid region: %s , %d, %d.", gene.getSymbol(), start, end);
                LOG.error(msg);
                System.out.println(msg);
                continue;
            }
            bs.set(start, end + 1);
        }

        int prevEndPos = 0;
        List<Integer[]> locationPairs = new ArrayList<Integer[]>();
        int nextIntronStart = bs.nextClearBit(prevEndPos + 1);
        while (nextIntronStart < geneLength && prevEndPos != -1) {
            int intronEnd;
            int nextSetBit = bs.nextSetBit(nextIntronStart);

            if (nextSetBit == -1) {
                intronEnd = geneLength;
                prevEndPos = -1;
            } else {
                intronEnd = nextSetBit - 1;
                prevEndPos = intronEnd;
            }

            int newLocStart = nextIntronStart + geneStart;
            int newLocEnd = intronEnd + geneStart;

            locationPairs.add(new Integer[] {newLocStart, newLocEnd});

            nextIntronStart = bs.nextClearBit(prevEndPos + 1);
        }
        String geneSymbol = gene.getSymbol();
        String strand = geneLoc.getStrand();

        int numIntron = locationPairs.size();
        for (int i = 0; i < numIntron; i++) {
            int intronIndex;
            if (strand.equals("1")) {
                intronIndex = i + 1;
            } else {
                intronIndex = numIntron - i;
            }

            Integer locStart = locationPairs.get(i)[0];
            Integer locEnd = locationPairs.get(i)[1];

            String identifier = "intron_chr" + chr.getPrimaryIdentifier()
                    + "_" + locStart + ".." + locEnd;

            if (intronMap.get(identifier) == null) {
                Class<?> intronCls = model.getClassDescriptorByName("Intron").getType();
                Intron intron = (Intron)
                        DynamicUtil.createObject(Collections.singleton(intronCls));
                Location location =
                        (Location) DynamicUtil.createObject(Collections.singleton(Location.class));

                intron.setChromosome(chr);
                intron.setOrganism(chr.getOrganism());
                intron.addDataSets(dataSet);
                intron.setPrimaryIdentifier(identifier);
                intron.setSymbol(geneSymbol + "-intron-" + intronIndex);
                intron.setFieldValue("genes", Collections.singleton(gene));

                location.setStart(locStart);
                location.setEnd(locEnd);
                location.setStrand(strand);
                location.setFeature(intron);
                location.setLocatedOn(chr);
                location.addDataSets(dataSet);

                intron.setChromosomeLocation(location);
                osw.store(location);

                int length = locEnd - locStart + 1;
                intron.setLength(new Integer(length));
                intronMap.put(identifier, intron);
            }
        }
        return numIntron;
    }

}

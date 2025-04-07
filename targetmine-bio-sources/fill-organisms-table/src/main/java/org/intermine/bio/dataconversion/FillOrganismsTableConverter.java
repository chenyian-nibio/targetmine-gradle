package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.ResultsRow;

/**
 * This one is to replace the fill-organisms source. 
 * Read the processed tables from downloaded Taxonomy DB, instead of fetching from NCBI via esummary.
 * 
 * @author chenyian
 */
public class FillOrganismsTableConverter extends BioFileConverter {
	protected static final Logger LOG = LogManager.getLogger(FillOrganismsTableConverter.class);
    //
    private static final String DATASET_TITLE = "Taxonomy";
    private static final String DATA_SOURCE_NAME = "NCBI";

    private Map<String, String> taxonNameMap = new HashMap<String, String>();

	private String osAlias = null;
	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	private Set<String> hasShortName = new HashSet<String>();
	public void setHasShortName(String taxonIds) {
		this.hasShortName = new HashSet<String>(Arrays.asList(taxonIds.split(" ")));
		LOG.info("Only the following organisms contain 'shortName': "
				+ StringUtils.join(hasShortName, ","));
	}

	private File taxonNamesFile;
	public void setTaxonNamesFile(File taxonNamesFile) {
		this.taxonNamesFile = taxonNamesFile;
	}

    private Set<String> processedTaxonIds = new HashSet<String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FillOrganismsTableConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        readTaxonNamesFile();

        Set<String> taxonIds = getTaxonIds();

        System.out.println(String.format("%d %s object(s) to be processed.", taxonIds.size(), "Organism"));
        LOG.info(String.format("%d %s object(s) to be processed.", taxonIds.size(), "Organism"));

		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);

        int i = 0;
        iterator.next(); //get rid of the header (the first line)
        while (iterator.hasNext()) {
            // taxonId	rank	k	p	c	o	f	g	s
			String[] cols = iterator.next();
            String taxonId = cols[0];
            if (taxonIds.contains(taxonId)) {
                Item item = createItem("Organism");
                item.setAttribute("taxonId", taxonId);
                String name = taxonNameMap.get(taxonId);
                if (name != null) {
                    item.setAttribute("name", name);
                }
                if (cols[1].equals("species")) {
                    String genusName = taxonNameMap.get(cols[7]);
                    if (genusName != null) {
                        item.setAttribute("genus", genusName);
                    }
                    String speciesName = taxonNameMap.get(cols[8]);
                    if (speciesName != null) {
                        item.setAttribute("species", speciesName);
                    }
                    if (hasShortName.contains(taxonId)) {
                        String shortName = genusName.charAt(0) + ". " + speciesName;
                        item.setAttribute("shortName", shortName);
                        System.out.println("assign the short name: " + shortName);
                    }
                }

                Item taxonomy = createItem("Taxonomy");
                taxonomy.setAttribute("taxonId", taxonId);
                store(taxonomy);

                item.setReference("taxonomy", taxonomy);
                store(item);

                processedTaxonIds.add(taxonId);
                i++;
            }
        }

        int n = 0;
        // Set<String> secondStep = new HashSet<String>();
        for (String taxonId : taxonIds) {
            if (!processedTaxonIds.contains(taxonId)) {
                String name = taxonNameMap.get(taxonId);
                if (name != null) {
                    Item item = createItem("Organism");
                    item.setAttribute("taxonId", taxonId);
                    item.setAttribute("name", name);

                    Item taxonomy = createItem("Taxonomy");
                    taxonomy.setAttribute("taxonId", taxonId);
                    store(taxonomy);
    
                    item.setReference("taxonomy", taxonomy);
                    store(item);

                    // secondStep.add(taxonId);

                    n++;
                }
            }
        }

        // System.out.println(StringUtils.join(secondStep, ","));
        // LOG.info(StringUtils.join(secondStep, ","));

		System.out.println(String.format("%d taxonids were processed at the first step.", i));
		LOG.info(String.format("%d taxonids were processed at the first step.", i));
		System.out.println(String.format("%d taxonids were processed at the second step.", n));
		LOG.info(String.format("%d taxonids were processed at the second step.", n));
		System.out.println(String.format("%d organism objects were created.", i + n));
		LOG.info(String.format("%d organism objects were created.", i + n));
    }

	private void readTaxonNamesFile() {
		if (taxonNamesFile == null) {
			throw new NullPointerException("taxonNamesFile property is missing");
		}
		try {
            System.out.println("Reading taxonNamesFile...");
			Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new FileReader(taxonNamesFile));
			
			while(iterator.hasNext()) {
				String[] cols = iterator.next();
				taxonNameMap.put(cols[0], cols[1]);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Set<String> getTaxonIds() throws Exception {
		Query q = new Query();
		QueryClass c = new QueryClass(Class.forName("org.intermine.model.bio.Organism"));
		QueryField f1 = new QueryField(c, "taxonId");
		q.addFrom(c);
		q.addToSelect(f1);
		q.setDistinct(true);

		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Set<String> ret = new HashSet<String>();
		Iterator iterator = os.execute(q).iterator();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			ret.add(rr.get(0));
		}
		return ret;
	}

}

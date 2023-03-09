package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * Parser for the BED file of EPDnew (https://epd.epfl.ch/)
 * 
 * @author chenyian
 */
public class EpdnewConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "EPDnew";
    private static final String DATA_SOURCE_NAME = "Eukaryotic Promoter Database";

    private String taxonId;
    public void setTaxonId(String taxonId) {
    	this.taxonId = taxonId;
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public EpdnewConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
	public void process(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String chr = cols[0].substring(3);
			String start = cols[1];
			String end = cols[2];
			String name = cols[3];
			String strand = cols[5];

			Item item = createItem("Promoter");
			item.setReference("organism", getOrganism(taxonId));
			item.setAttribute("primaryIdentifier", name);

			String chromosomeRefId = getChromosome(taxonId, chr);

			Item location = createItem("Location");
			location.setAttribute("start", start);
			location.setAttribute("end", end);
			location.setAttribute("strand", strand.equals("+") ? "1" : "-1");
			location.setReference("feature", item);
			location.setReference("locatedOn", chromosomeRefId);

			item.setReference("chromosome", chromosomeRefId);
			item.setReference("chromosomeLocation", location);

			int length = Integer.valueOf(end) - Integer.valueOf(start) + 1;
			item.setAttribute("length", String.valueOf(length));

			store(location);
			store(item);

		}

	}
    
	private Map<String, String> chromosomeMap = new HashMap<String, String>();
	private String getChromosome(String taxonId, String identifier) throws ObjectStoreException {
		String key = taxonId + "-" + identifier;
		String ret = chromosomeMap.get(key);
		if (ret == null) {
			Item chromosome = createItem("Chromosome");
			chromosome.setReference("organism", getOrganism(taxonId));
			chromosome.setAttribute("primaryIdentifier", identifier);
			chromosome.setAttribute("symbol", identifier);
			store(chromosome);
			ret = chromosome.getIdentifier();
			chromosomeMap.put(key, ret);
		}
		return ret;
	}

}

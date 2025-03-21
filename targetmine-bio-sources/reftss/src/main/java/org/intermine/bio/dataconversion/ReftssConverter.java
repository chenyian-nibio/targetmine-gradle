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
 * Parser for the file "refTSS_v4.1_human_coordinate_ALL_annotation.txt", 
 * which is an extention of "refTSS_v4.1_human_coordinate.hg38.bed.txt", 
 * provided by a collaborator from Riken
 * 
 * @author chenyian
 */
public class ReftssConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "refTSS4";
    private static final String DATA_SOURCE_NAME = "refTSS";

    private String taxonId;
    public void setTaxonId(String taxonId) {
    	this.taxonId = taxonId;
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public ReftssConverter(ItemWriter writer, Model model) {
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
			String chr = cols[0].substring(3); // get rid of 'chr'
			String start = cols[1];
			String end = cols[2];
			String strand = cols[3];
			// String geneId = cols[6];
			String geneSymbol = cols[10];
			// String detailedAnno = cols[14];
			String reftssId = cols[19];

			Item item = createItem("TSS");
			item.setReference("organism", getOrganism(taxonId));
			item.setAttribute("primaryIdentifier", reftssId);
			// item.setAttribute("name", detailedAnno);
			item.setAttribute("symbol", geneSymbol + "-tss");

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

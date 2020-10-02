package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
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
 * 
 * @author chenyian
 */
public class PossumConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "PoSSuM";
    private static final String DATA_SOURCE_NAME = "PoSSuM";

	private File ligandFile;
	public void setLigandFile(File ligandFile) {
		this.ligandFile = ligandFile;
	}

	private Map<String, Item> pocketMap = new HashMap<String, Item>();
	
	private Map<String, String> ligandChemblIdMap = new HashMap<String, String>();
	private Map<String, String> ligandNameMap = new HashMap<String, String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public PossumConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	if (ligandChemblIdMap.isEmpty()) {
    		readLigandChemblIdMap();
    	}
    	
		Iterator<String[]> iterator = FormattedTextParser.parseCsvDelimitedReader(reader);
		String[] header = iterator.next();
		Map<String,Integer> headerMap = new HashMap<String, Integer>();
		for (int i = 0; i < header.length; i++) {
			headerMap.put(header[i], Integer.valueOf(i));
		}
		
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			
			// there are 2 pockets in a line Query and Target

			Integer qPocketIndex = headerMap.get("Q:PocketIndex");
//			if (qPocketIndex == null) {
//				throw new RuntimeException("Cannot find the index of 'Q:PocketIndex' in the file: " + getCurrentFile().getName());
//			}
			String qPocketId = cols[qPocketIndex];
			if (pocketMap.get(qPocketId) == null) {
				Item item = createItem("LigandBindingPocket");
				item.setAttribute("identifier", qPocketId);
				item.setAttribute("pocketType", "known");
				item.setReference("ligand", getLigand(cols[headerMap.get("Q:HET_Code")]));
				item.setReference("chain", getProteinChain(cols[headerMap.get("Q:PDB_ID")], cols[headerMap.get("Q:ChainID")]));
				pocketMap.put(qPocketId, item);
			}
			
			String tPocketId = cols[headerMap.get("T:PocketIndex").intValue()];
			if (pocketMap.get(tPocketId) == null) {
				Item item = createItem("LigandBindingPocket");
				item.setAttribute("identifier", tPocketId);
				Integer tHetCodeIndex = headerMap.get("T:HET_Code");
				if (tHetCodeIndex == null) {
					item.setAttribute("pocketType", "putative");
				} else {
					item.setAttribute("pocketType", "known");
					item.setReference("ligand", getLigand(cols[tHetCodeIndex]));
				}
				item.setReference("chain", getProteinChain(cols[headerMap.get("T:PDB_ID")], cols[headerMap.get("T:ChainID")]));
				pocketMap.put(tPocketId, item);
			}
			
			pocketMap.get(qPocketId).addToCollection("similarPockets", pocketMap.get(tPocketId));
			pocketMap.get(tPocketId).addToCollection("similarPockets", pocketMap.get(qPocketId));
		}
    }
    
    @Override
    public void close() throws Exception {
    	store(pocketMap.values());
    }

	private void readLigandChemblIdMap() throws Exception{
		Iterator<String[]> iterator = FormattedTextParser.parseCsvDelimitedReader(new FileReader(ligandFile));
		// ignore the header (slightly risky)
		iterator.next();
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			ligandChemblIdMap.put(cols[1], cols[3]);
			ligandNameMap.put(cols[1], cols[4]);
		}
	}
	
	private Map<String, String> proteinChainMap = new HashMap<String, String>();
	private String getProteinChain(String pdbId, String chainId) throws ObjectStoreException {
		String identifier = pdbId.toLowerCase() + chainId;
		String ret = proteinChainMap.get(identifier);
		if (ret == null) {
			Item item = createItem("ProteinChain");
			item.setAttribute("pdbId", pdbId.toLowerCase());
			item.setAttribute("chain", chainId);
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			proteinChainMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> ligandMap = new HashMap<String, String>();
	private String getLigand(String hetCode) throws ObjectStoreException {
		String ret = ligandMap.get(hetCode);
		if (ret == null) {
			Item item = createItem("Ligand");
			item.setAttribute("hetCode", hetCode);
			String name = ligandNameMap.get(hetCode);
			if (name != null) {
				item.setAttribute("name", name);
			}
			item.setReference("pdbCompound", getPdbCompound(hetCode));
			String chemblId = ligandChemblIdMap.get(hetCode);
			if (chemblId != null) {
				item.setReference("chemblCompound", getChemblCompound(chemblId));
			}
			store(item);
			ret = item.getIdentifier();
			ligandMap.put(hetCode, ret);
		}
		return ret;
	}

	private Map<String, String> compoundMap = new HashMap<String, String>();
	private String getChemblCompound(String chemblId) throws ObjectStoreException {
		String ret = compoundMap.get(chemblId);
		if (ret == null) {
			Item item = createItem("ChemblCompound");
			item.setAttribute("originalId", chemblId);
//			item.setAttribute("identifier", String.format("ChEMBL:%s", chemblId));
			store(item);
			ret = item.getIdentifier();
			compoundMap.put(chemblId, ret);
		}
		return ret;
	}
	private String getPdbCompound(String identifier) throws ObjectStoreException {
		String ret = compoundMap.get(identifier);
		if (ret == null) {
			Item item = createItem("PDBCompound");
			item.setAttribute("originalId", identifier);
//			item.setAttribute("identifier", String.format("PDBCompound:%s", identifier));
			store(item);
			ret = item.getIdentifier();
			compoundMap.put(identifier, ret);
		}
		return ret;
	}

}

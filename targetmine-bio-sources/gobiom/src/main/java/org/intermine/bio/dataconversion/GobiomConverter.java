package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.inchi.InChIGenerator;
import org.openscience.cdk.inchi.InChIGeneratorFactory;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.smiles.SmilesParser;

import net.sf.jniinchi.INCHI_RET;
/**
 * @author Ishikawa.Motokazu
 * 
 * (refinded by chenyian)
 */
public class GobiomConverter extends BioFileConverter {
	
	private static final Logger LOG = LogManager.getLogger(GobiomConverter.class);
	
	private static final String DATASET_TITLE = "GOBIOM";
	private static final String DATA_SOURCE_NAME = "GOBIOM";

	private static final String HOMO_SAPIENS_TAXON_ID = "9606";

	private static Set<String> GENE_RELATED_BIOMARKERS = new HashSet<String>();
	{
		GENE_RELATED_BIOMARKERS.add("Variation");
		GENE_RELATED_BIOMARKERS.add("Protein");
		GENE_RELATED_BIOMARKERS.add("Gene");
		GENE_RELATED_BIOMARKERS.add("RNA");
		GENE_RELATED_BIOMARKERS.add("Peptide");
		GENE_RELATED_BIOMARKERS.add("Epigenetics");
		GENE_RELATED_BIOMARKERS.add("Antibody");
		GENE_RELATED_BIOMARKERS.add("Haplotype");
		GENE_RELATED_BIOMARKERS.add("Noncoding RNA");
	}
	
	private InChIGeneratorFactory factory;
	
	/**
	 * Construct a new GobiomConverter.
	 * 
	 * @param model
	 *            the Model used by the object store we will write to with the ItemWriter
	 * @param writer
	 *            an ItemWriter used to handle Items created
	 */
	public GobiomConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (resolver == null) {
			resolver = new UMLSResolver(mrConsoFile, mrStyFile);
		}
		
		LOG.info("Start to process GOBIOM");
		
		/**
		 * Processing BIOM_STRUCTURE_MASTER.csv file to collect biomarker information
		 */
		int numberOfConversionFailure = 0;
		
		if (factory == null) {
			factory = InChIGeneratorFactory.getInstance();
		}
		
		Iterator<String[]> iterator = FormattedTextParser.parseCsvDelimitedReader(reader);
		iterator.next(); // Skip header
		
		while( iterator.hasNext() ) {
			
			String[] biomStructureDetailsRow = iterator.next();
			
			String identifier = biomStructureDetailsRow[0];
			String smiles = biomStructureDetailsRow[2];
			String CasNo = biomStructureDetailsRow[5];
			String biomarkerName = biomStructureDetailsRow[6];
			String biomarkerType = biomStructureDetailsRow[7];
			String chemicalNature = biomStructureDetailsRow[8];
			String therapeuticClass = biomStructureDetailsRow[9];
			String diseaseName = biomStructureDetailsRow[10];
			String diseaseType = biomStructureDetailsRow[11];
			String locusId = biomStructureDetailsRow[12];
			String multipleloci = biomStructureDetailsRow[13];
			String status = biomStructureDetailsRow[18];
			String rsNumber = biomStructureDetailsRow[25];
			
			Item item = createItem("Biomarker");
			item.setAttribute("identifier", identifier);
			item.setAttribute("name", biomarkerName);
			item.setAttribute("biomarkerType", biomarkerType);
			item.setAttribute("chemicalNature", chemicalNature);
			item.setAttribute("therapeuticClass", therapeuticClass);
			item.setAttribute("status", status);

			/**
			 * According to chemical nature of this biomarker, substantial instance will be created below
			 */
			if (GENE_RELATED_BIOMARKERS.contains(chemicalNature)) {
				boolean flag = false;
				Set<String> geneIds = new HashSet<String>();
				if (!"".equals(locusId) && isValidId(locusId)) {
					geneIds.add(locusId);
				}
				if (!"".equals(multipleloci)) {
					for (String id : multipleloci.split("; ")) {
						if (isValidId(id)) {
							geneIds.add(id);
						}
					}
				}
				if (geneIds.size() > 0) {
					for (String id : geneIds) {
						item.addToCollection("genes", getGene(id));
					}
					flag = false;
				}
				
				if (!"".equals(rsNumber)) {
					// Register "Variation" biomarker as a SNP instance only if it has rs number
					item.setReference("snp", getSnp(rsNumber));
					flag = false;
				}
				
				if (flag) {
					continue;
				}
				
			} else if ("Scoring scale".equals(biomarkerType)) {
				// unchecked
				Item scoringScaleItem = createItem("ScoringScale");
				scoringScaleItem.setAttribute("name", biomarkerName);
				store(scoringScaleItem);
				item.setReference("scoringScale", scoringScaleItem);
				
			} else if ("Chemical compound".equals(chemicalNature)) {
				
				String compound = compoundMap.get(biomarkerName);
				if (compound == null && !"".equals(smiles)) {
					String inchikey = inchiKeyMap.get(smiles);
					if (inchikey == null) {
						SmilesParser sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
						IAtomContainer mol = null;
						try {
							// if conversion from SMILES to InChIkey fails, skip this entry
							mol = sp.parseSmiles(smiles);
							InChIGenerator generator = factory.getInChIGenerator(mol);
							
							if (generator.getReturnStatus() == INCHI_RET.OKAY) {
								inchikey = generator.getInchiKey();
							} else {
								inchikey = "";
							}
							
						} catch (InvalidSmilesException e) {
							numberOfConversionFailure += 1;
							inchikey = "";
						}
						inchiKeyMap.put(smiles, inchikey);
					}
					
					compound = getCompound(biomarkerName, inchikey, CasNo);
					
					compoundMap.put(biomarkerName, compound);
					
				} else {
					continue;
				}
				
				item.setReference("compound", compound);
				
			} else {
				continue;
			}
			
			// in case we cannot find the umls term, also save the original name
			item.setAttribute( "diseaseType", diseaseType );
			item.setAttribute( "diseaseName", diseaseName );

			/**
			 * Finding out UMLS CUI for this disease
			 */
			String cui = getCui(diseaseName);
			if (!StringUtils.isEmpty(cui)) {
				item.setReference("umlsTerm", getUmlsTerm(cui));
			}

			store(item);
		}
		
		LOG.info("Number of conversion(SMILES->InChIkey) failures: " + numberOfConversionFailure);
		
	}
	
	// smiles -> inchiKey
	private Map<String, String> inchiKeyMap = new HashMap<String, String>();
	// biomarkerName -> compound item reference
	private Map<String, String> compoundMap = new HashMap<String, String>();

	private String getCompound(String name, String inchiKey, String casRegistryNumber) throws ObjectStoreException {
		Item item = createItem("GobiomCompound");
		item.setAttribute("identifier", name);
		item.setAttribute("name", name);
		if (!StringUtils.isEmpty(inchiKey)) {
			item.setAttribute("inchiKey", inchiKey);
			String compoundGroupId = inchiKey.substring(0, inchiKey.indexOf("-"));
			item.setReference("compoundGroup", getCompoundGroup(compoundGroupId, name));
		}
		if (!"".equals(casRegistryNumber))
			item.setAttribute("casRegistryNumber", casRegistryNumber);
		store(item);
		return item.getIdentifier();
	}
	
	private Map<String, String> compoundGroupMap = new HashMap<String, String>();

	private String getCompoundGroup(String compoundGroupId, String name) throws ObjectStoreException {
		String ret = compoundGroupMap.get(compoundGroupId);
		if (ret == null) {
			Item item = createItem("CompoundGroup");
			item.setAttribute("identifier", compoundGroupId);
			item.setAttribute("name", name);
			store(item);
			ret = item.getIdentifier();
			compoundGroupMap.put(compoundGroupId, ret);
		}
		return ret;
	}
	
	private Map<String, String> umlsTermMap = new HashMap<String, String>();

	private String getUmlsTerm(String cui) throws ObjectStoreException {
		String ret = umlsTermMap.get(cui);
		if (ret == null) {
			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", "UMLS:" + cui);
			store(item);
			ret = item.getIdentifier();
			umlsTermMap.put(cui, ret);
		}
		return ret;
	}
	
	private Map<String, String> geneMap = new HashMap<String, String>();

	private String getGene( String geneId ) throws ObjectStoreException {
		
		String ret = geneMap.get( geneId );
		if (ret == null) {
			Item item = createItem( "Gene" );
			item.setAttribute( "primaryIdentifier", geneId );
			item.setReference( "organism", getOrganism( HOMO_SAPIENS_TAXON_ID ) );
			store(item);
			geneMap.put( geneId, item.getIdentifier() );
		}
		return geneMap.get( geneId );
		
	}
	
	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
	}
	
	private Map<String, String> snpMap = new HashMap<String, String>();

	private String getSnp(String identifier) throws ObjectStoreException {
		String ret = snpMap.get(identifier);
		if (ret == null) {
			Item item = createItem("SNP");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			snpMap.put(identifier, ret);
		}
		return ret;
	}

	public static boolean isValidId(String s) {
		if (s == null || s.isEmpty())
			return false;
		for (int i = 0; i < s.length(); i++) {
			if (i == 0 && s.charAt(i) == '0') {
				return false;
			}
			if (Character.digit(s.charAt(i), 10) < 0)
				return false;
		}
		return true;
	}

	private UMLSResolver resolver;
	private File mrConsoFile;
	private File mrStyFile;

	public void setMrConsoFile(File mrConsoFile) {
		this.mrConsoFile = mrConsoFile;
	}

	public void setMrStyFile(File mrStyFile) {
		this.mrStyFile = mrStyFile;
	}

	private Map<String, String> diseaseTermCuiMap = new HashMap<String, String>();

	private String getCui(String diseaseName) {
		String ret = diseaseTermCuiMap.get(diseaseName);
		if (ret == null) {
			ret = resolver.getIdentifier(diseaseName);

			if (ret == null && diseaseName.contains("(disorder)")) {
				ret = resolver.getIdentifier(diseaseName.replaceAll("\\(disorder\\)", "").trim());
			}

			if (ret == null) {
				ret = "";
				LOG.info("Cannot find CUI for the term: '" + diseaseName + "'");
			}
			diseaseTermCuiMap.put(diseaseName, ret);
		}
		return ret;
	}

}

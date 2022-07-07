package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * @author Ishikawa.Motokazu
 * 
 * (refined by chenyian) 
 */
public class OrphanetConverter extends BioFileConverter {
	
	private static final Logger LOG = LogManager.getLogger(OrphanetConverter.class);
	
	private static final String DATASET_TITLE = "Orphanet";
	private static final String DATA_SOURCE_NAME = "Orphanet";
	
	private static final String HOMO_SAPIENS_TAXON_ID = "9606";
	
	private Pattern pubmedIdPattern = Pattern.compile("(^\\d+)"); 
	
	private File humanGeneInfoFile;
	private File ordoOwlFile;
	
	// key is CUI, value is a reference to DiseaseTerm Item
	private Map<String, String> diseaseTermMap = new HashMap<String, String>();
	// key is disorder, value is a reference to DiseaseTerm Item
	private Map<String, String> disorderMap = new HashMap<String, String>();
	// key is gene symbol, value is a reference to Gene Item
	private Map<String, String> geneMap = new HashMap<String, String>();
	// key is gene symbol, value is Entrez Gene ID
	private Map<String, String> geneSymbolMap = new HashMap<String, String>();
	// key is PubMed ID, value is a reference to Publication Item
	private Map<String, String> publicationMap = new HashMap<String, String>();
	
	/**
	 * Construct a new OrphanetConverter.
	 * 
	 * @param model
	 *            the Model used by the object store we will write to with the ItemWriter
	 * @param writer
	 *            an ItemWriter used to handle Items created
	 */
	public OrphanetConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		
		LOG.info( "Start parseHumanGeneInfoFile()" );
		/**
		 * Collecting orphanet term and CUI pair information into cuiMap dictionary
		 */
		parseHumanGeneInfoFile();
		
		LOG.info( "Start parseOrdoOwlFile()" );
		/**
		 * Collecting orphanet term and CUI pair information into cuiMap dictionary
		 */
		parseOrdoOwlFile();
		
		LOG.info( "Start parsing orphanet data" );
		
		/**
		 * Parsing orphanet data
		 */
		int counterRegisteredDisease = 0;
		
		Builder parser = new Builder();
		Document doc = parser.build( reader );
		Elements disorderList = doc.getRootElement().getChildElements("DisorderList");
		if( disorderList.size() != 1) {
			throw new RuntimeException( "ERROR: en_product6.xml is not valid." );
		}
		Elements disorders = disorderList.get(0).getChildElements();
		for ( int i = 0; i < disorders.size(); i++ ) {
			
			Element disorder = disorders.get(i);
			Element name = disorder.getChildElements("Name").get(0);
			String disorderName = name.getValue();
			Elements disorderGeneAssociations = disorder.getChildElements("DisorderGeneAssociationList").get(0).getChildElements();
			
			for ( int m = 0; m < disorderGeneAssociations.size(); m++ ) {
				
				Element association = disorderGeneAssociations.get( m );
				String sourceOfValidation = association.getChildElements("SourceOfValidation").get(0).getValue();
				String geneSymbol = association.getChildElements("Gene").get(0).getChildElements("Symbol").get(0).getValue();
				String associationType = association.getChildElements("DisorderGeneAssociationType").get(0).getChildElements("Name").get(0).getValue();
				
				if ( store_gene_disease_relation( disorderName, geneSymbol, associationType, sourceOfValidation ) ) {
					counterRegisteredDisease += 1;
				}
				
			}
			
		}
		
		LOG.info( "Number of registered DiseaseTerm items: " + disorderMap.size() );
		LOG.info( "Number of registered Disease items: " + counterRegisteredDisease );
		
	}
	
	private boolean store_gene_disease_relation( String disorderName, String geneSymbol, String associationType, String sourceOfValidation ) throws ObjectStoreException  {

		String diseaseTerm = disorderMap.get( disorderName );
		LOG.info( "disorderName="+disorderName+", geneSymbol="+geneSymbol+", associationType="+associationType+", diseaseTerm="+diseaseTerm+", sourceOfValidation="+sourceOfValidation );
		
		if( null == diseaseTerm ) {
			return false;
		}
		
		String geneId = geneSymbolMap.get(geneSymbol);
		// If Gene ID doesn't exist for this symbol, we should skip this gene
		if (geneId == null) {
			return false;
		}
		
		Item item = createItem("Disease");
		item.setAttribute( "associationType", associationType );
		item.setReference( "gene", getGene(geneId) );
		item.setReference( "diseaseTerm", diseaseTerm );
		
		if (null != sourceOfValidation && !"".equals(sourceOfValidation)) {
			for (String sov : sourceOfValidation.split("_")) {
				Matcher matcher = pubmedIdPattern.matcher(sov);
				if (matcher.find()) {
					String pmid = matcher.group(1);
					item.addToCollection("publications", getPublication(pmid));
				} else {
					LOG.info("CANNOT match the pmid: " + sov);
				}
				
			}
		}
		
		store( item );
		return true;
		
	}

	private String getPublication(String pubmedId) throws ObjectStoreException {
		String ret = publicationMap.get(pubmedId);
		if (ret == null) {
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubmedId);
			store(item);
			ret = item.getIdentifier();
			publicationMap.put(pubmedId, ret);
		}
		return ret;
	}
	
	private String getGene(String geneId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			item.setReference("organism", getOrganism(HOMO_SAPIENS_TAXON_ID));
			store(item);
			ret = item.getIdentifier();
			geneMap.put(geneId, ret);
		}
		return ret;
	}
	
	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
	}
	
	private void parseHumanGeneInfoFile() throws FileNotFoundException, IOException {

		Iterator<String[]> humanGeneInfoIterator = FormattedTextParser
				.parseTabDelimitedReader(new FileReader(this.humanGeneInfoFile));
		while (humanGeneInfoIterator.hasNext()) {
			String[] humanGeneInfoRow = humanGeneInfoIterator.next();

			if (humanGeneInfoRow.length < 16) {
				continue;
			}

			String idColumn = humanGeneInfoRow[1];
			// to prevent unofficial symbol happened, use Symbol_from_nomenclature_authority
			// instead
			String symbolColumn = humanGeneInfoRow[10];

			if (idColumn == null || symbolColumn == null) {
				continue;
			}
			geneSymbolMap.put(symbolColumn, idColumn);

		}

	}
	
	private void parseOrdoOwlFile() throws IOException, ValidityException, ParsingException, ObjectStoreException {

		Builder parser = new Builder();
		Document doc = parser.build( new FileReader( this.ordoOwlFile ) );
		Element rootElement = doc.getRootElement();
		
		Elements owlClasses = rootElement.getChildElements( "Class","http://www.w3.org/2002/07/owl#" );
		
		for ( int i = 0; i < owlClasses.size(); i++ ) {
			
			Element owlClass = owlClasses.get( i );
			String cui = null;
			String orpha = null;
			String disorder = null;
			
			// Getting UMLS CUI
			Elements hasDbXrefs = owlClass.getChildElements( "hasDbXref", "http://www.geneontology.org/formats/oboInOwl#" );
			
			for ( int m = 0; m < hasDbXrefs.size(); m++ ) {
				
				Element hasDbXref = hasDbXrefs.get( m );
				if ( hasDbXref.getValue().startsWith( "UMLS:" ) ) {
					
					cui = hasDbXref.getValue().replace( "UMLS:", "" );
					break;
					
				}
				
			}
			
			// Getting ORPHA number
			Elements notations = owlClass.getChildElements( "notation", "http://www.w3.org/2004/02/skos/core#" );
			for ( int m = 0; m < notations.size(); m++ ) {
				Element notation = notations.get( m );
				if ( notation.getValue().startsWith( "ORPHA:" ) ) {
					
					orpha = notation.getValue();
					break;
					
				}
			}
			
			// Getting disorder name
			Elements labels = owlClass.getChildElements( "label", "http://www.w3.org/2000/01/rdf-schema#" );
			if ( 0 != labels.size() ) {
				
				disorder = labels.get( 0 ).getValue();
				
			}
			
			// Register disorder & cui pair
			if ( null != cui && null != disorder ) {
				
				if ( ! diseaseTermMap.containsKey( cui ) ) {
					
					Item item = createItem( "DiseaseTerm" );
					item.setAttribute( "identifier", cui );
					item.setAttribute( "name", disorder );
					item.setAttribute( "description", disorder );
					item.setAttribute( "namespace", "ORDO" );
					store( item );
					diseaseTermMap.put( cui, item.getIdentifier() );
					
				}
				
				disorderMap.put( disorder, diseaseTermMap.get( cui ) );
				
			}else if( null != orpha && null != disorder ) {
				
				Item item = createItem( "DiseaseTerm" );
				item.setAttribute( "identifier", orpha );
				item.setAttribute( "name", disorder );
				item.setAttribute( "description", disorder );
				item.setAttribute( "namespace", "ORDO" );
				store( item );
				
				String ret = item.getIdentifier();
				disorderMap.put( disorder, ret );
				
			}
			
		}
		
	}
	
	public void setHumanGeneInfoFile(File humanGeneInfoFile) {
		this.humanGeneInfoFile = humanGeneInfoFile;
	}

	public void setOrdoOwlFile(File ordoOwlFile) {
		this.ordoOwlFile = ordoOwlFile;
	}

}

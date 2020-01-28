package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.*;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Integrate dbsnp in advance.
 * Read hgmd database in mysql.
 * mysql db setting wrote '~/.intermine/targetmine.properties'.
 *
 * @author
 */
public class HgmdConverter extends BioDBConverter {
    private static final Logger LOG = LogManager.getLogger(HgmdConverter.class);
    // 
    private static final String DATASET_TITLE = "hgmd";
    private static final String DATA_SOURCE_NAME = "hgmd";
    private GeneIdFinder geneIdFinder;
    private Map<String, String> hgmdMap = new HashMap<String, String>();
    private Map<String, String> geneMap = new HashMap<String, String>();
    private Map<String, String> snpMap = new HashMap<String, String>();
    private Map<String, String> publicationMap = new HashMap<String, String>();
    private Map<String, String> variationAnnotationMap = new HashMap<String, String>();

    private String osAlias = null;

    private Set<String> snpIdSet = new HashSet<String>();

    private Map<String, String> snpFunctionNameMap = new HashMap<String, String>();
    private Set<String> snpFunctionNameSet = new HashSet<String>();
    private Set<String> umlsTermSet = new HashSet<String>();

    private static Map<String, String> mutypeToSnpFunctionNames = new HashMap<String, String>();

    static {
        Map<String, String> p = new HashMap<>();
        // key:hgmd_pro.allmut.mutype , value:snpfunction.name
        mutypeToSnpFunctionNames.put("frameshift", "frameshift");
        mutypeToSnpFunctionNames.put("missense", "missense");
        mutypeToSnpFunctionNames.put("nonsense", "STOP-GAIN");
        mutypeToSnpFunctionNames.put("nonstop", "STOP-LOSS");
        mutypeToSnpFunctionNames.put("synonymous", "cds-synon");
    }

    /**
     * Construct a new HgmdConverter.
     *
     * @param database the database to read from
     * @param model    the Model used by the object store we will write to with the ItemWriter
     * @param writer   an ItemWriter used to handle Items created
     */
    public HgmdConverter(Database database, Model model, ItemWriter writer) {
        super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
    }


    /**
     * {@inheritDoc}
     */
    public void process() throws Exception {
    	geneIdFinder = new GeneIdFinder(osAlias, this);
        // Get data already registered in other Converter.
        getSnpIds();
        getSnpFunctionNames();
        getUMLSTerm();

        // a database has been initialised from properties starting with db.hgmd
        Connection connection = getDatabase().getConnection();
        Statement stmt = connection.createStatement();

        // TODO: SQL 要確認
        String queryAllmut = "SELECT * from hgmd_pro.allmut " +
                "LEFT JOIN hgmd_pro.mutnomen ON " +
                "hgmd_pro.allmut.acc_num = hgmd_pro.mutnomen.acc_num " +
                ";";

        ResultSet resAllmut = stmt.executeQuery(queryAllmut);
        while (resAllmut.next()) {
            // hgmd data input. return hgmd id.
            String hgmdId = createHgmd(resAllmut);

            // publication data input & reference hgmd.
            getPublication(resAllmut, hgmdId);

            // snp data input & reference hgmd. return snpId.
            // hgmd にdbsnpがあればそのまま使用、なければacc_numで代用
            String snpId = resAllmut.getString("dbsnp");
            if (StringUtils.isEmpty(snpId)) {
                snpId = resAllmut.getString("acc_num");
            }
            String snpRef = getSnp(resAllmut, snpId, hgmdId);
            // if hgmd contains dbsnp, only set reference snpid. else set snp and other data.
            if (!snpIdSet.contains(snpId)) {
                // SNPFunction data input. return SNPFunctionId.
                String snpFunctionRef = getOrCreateSnpFunction(resAllmut);
                String geneId = geneIdFinder.getGenePrimayIdBySynonym(resAllmut.getString("refCore"));
                String geneRef = null;
                if(geneId!=null) {
                	geneRef = geneIdFinder.getGeneRef(geneId);
                }

                // get variationAnnotation
                if (StringUtils.isEmpty(geneId)) geneId = "0";
                String variationId = combineString(snpId, geneId, "-");
                // VariationAnnotaition data input & reference geneId, SNPFunctionId, SNPId. return VariationAnnotation id.
                String variationAnnotationRef = getVariationAnnotation(variationId, geneRef, snpFunctionRef, snpRef);

                // SNPReference data input & get SNPReference identifier.
                getSNPReference(resAllmut, snpFunctionRef, variationAnnotationRef);
            }

        }

        String queryCui = "SELECT hgmd_pro.allmut.acc_num AS acc_num, " +
                "hgmd_pro.allmut.dbsnp AS dbsnp," +
                "hgmd_phenbase.phenotype_concept.cui AS cui " +
                "FROM hgmd_pro.allmut " +
                "JOIN hgmd_phenbase.hgmd_mutation ON " +
                "hgmd_pro.allmut.acc_num = hgmd_phenbase.hgmd_mutation.acc_num " +
                "JOIN hgmd_phenbase.phenotype_concept ON " +
                "hgmd_phenbase.hgmd_mutation.phen_id = hgmd_phenbase.phenotype_concept.phen_id " +
                ";";
        ResultSet resCui = stmt.executeQuery(queryCui);
        Map<String, Set<String>> hgmdsUmlsesMap = new HashMap<String, Set<String>>();
        while (resCui.next()) {
            // get Hgmd ref
            // hgmd にdbsnpがあればそのまま使用、なければacc_numで代用
            String snpId = resCui.getString("dbsnp");
            if (StringUtils.isEmpty(snpId)) {
                snpId = resCui.getString("acc_num");
            }
            String cui = "UMLS:"+resCui.getString("cui");
            String hgmdRef = hgmdMap.get(snpId);
            // hgmd がnullでなく、cuiに一致するデータがumlsにある場合
            if (!StringUtils.isEmpty(hgmdRef) && umlsTermSet.contains(cui)) {
                if (hgmdsUmlsesMap.get(cui) == null) {
                    hgmdsUmlsesMap.put(cui, new HashSet<String>());
                }
                hgmdsUmlsesMap.get(cui).add(hgmdRef);
            }
        }
        // create HGMD and UMLSTerm
        for (String cui : hgmdsUmlsesMap.keySet()) {
            getUmlses(cui, new ArrayList<String>(hgmdsUmlsesMap.get(cui)));
        }
        stmt.close();
        connection.close();
    }

    private String createHgmd(ResultSet response) throws Exception {
        String identifier = response.getString("acc_num");
        String description = response.getString("descr");
        String variantClass = response.getString("tag");

        String ret = hgmdMap.get(identifier);
        if (ret == null) {
            Item item = createItem("Hgmd");
            item.setAttribute("identifier", identifier);
            item.setAttribute("description", description);
            item.setAttribute("variantClass", variantClass);
            store(item);
            ret = item.getIdentifier();
            hgmdMap.put(identifier, ret);
        }
        return ret;
    }

    private String getPublication(ResultSet response, String hgmdId) throws Exception {
        String pubMedId = response.getString("pmid");
        if (StringUtils.isEmpty(pubMedId)) {
            return "";
        }
        String ret = publicationMap.get(pubMedId);
        if (ret == null) {
            // Publication set only pubMedId.
            Item item = createItem("Publication");
            item.setAttribute("pubMedId", pubMedId);
            item.setReference("hgmd", hgmdId);
            store(item);
            ret = item.getIdentifier();
            publicationMap.put(pubMedId, ret);
        }
        return ret;
    }
    private static final Pattern hgvsPattern = Pattern.compile("\\d+(\\w+)>(\\w+)");
    private static String convertHGVSTorefSnpAllele(String hgvs,String id) {
    	Matcher matcher = hgvsPattern.matcher(hgvs);
    	if(matcher.matches()) {
    		return matcher.group(1)+"/"+matcher.group(2);
    	}else {
    		LOG.warn("Unexpected hgvs " + hgvs +" at "+id);
    		return hgvs;
    	}
    }
    private String getSnp(ResultSet response, String identifier, String hgmdId) throws Exception {
        String ret = snpMap.get(identifier);

        if (ret == null) {
            Item item = createItem("SNP");

            // hgmd にdbsnpのIDが入っていればSNP にリンクを張る。
            if (snpIdSet.contains(identifier)) {
                // itemへのstore後のidを取得したいため、identifierのみ設定
                item.setAttribute("identifier", identifier);
            } else {
                // 無い場合はhgmdのデータを利用してSNPの情報を埋める
                item.setAttribute("identifier", identifier);

                // TODO: データの作り方 要確認 : allmut.hgvsまたはallmut.deletionまたはallmut.insertion
                String refSnpAllele = "";
                if (!StringUtils.isEmpty(response.getString("hgvs"))) {
                    refSnpAllele = convertHGVSTorefSnpAllele(response.getString("hgvs"), identifier);
                } else if (!StringUtils.isEmpty(response.getString("deletion"))) {
                    refSnpAllele = response.getString("deletion");
                } else if (!StringUtils.isEmpty(response.getString("insertion"))) {
                    refSnpAllele = response.getString("insertion");
                } else {
                    // hgvs, deletion, insertion が無ければ SNPではなく、Variant にデータを登録
                    item = createItem("Variant");
                    String variantId = response.getString("acc_num");
                    String description = response.getString("descr");
                    item.setAttribute("identifier", variantId);
                    item.setAttribute("description", description);
                    item.setReference("hgmd", hgmdId);
                    store(item);
                    ret = item.getIdentifier();
                    snpMap.put(identifier, ret);
                    return ret;
                }

                String coodStart = response.getString("startCoord");
                String chromosome = response.getString("chromosome");
                // TODO: データの作り方 要確認 : allmut.chromosomeとallmut.coordSTART
                String location = combineString(chromosome, coodStart, ":");
                // TODO: データの作り方　要確認 :  ?
                String orientation = "";

                if (!StringUtils.isEmpty(location)) {
                    item.setAttribute("location", location);
                }
                if (!StringUtils.isEmpty(chromosome)) {
                    item.setAttribute("chromosome", chromosome);
                }
                if (!StringUtils.isEmpty(refSnpAllele)) {
                    item.setAttribute("refSnpAllele", refSnpAllele);
                }
                if (!StringUtils.isEmpty(orientation)) {
                    item.setAttribute("orientation", orientation);
                }
            }
            item.setReference("hgmd", hgmdId);
            store(item);
            ret = item.getIdentifier();
            snpMap.put(identifier, ret);
        }

        return ret;
    }

    private String getUmlses(String cui, List<String> hgmdsUmlses) throws Exception {
        Item item = createItem("UMLSTerm");
        item.setAttribute("identifier", cui);
        item.setCollection("hgmds", hgmdsUmlses);
        store(item);
        return item.getIdentifier();
    }

    /**
     * DB read SNP column "identifier".
     *
     * @throws Exception
     */
    private void getSnpIds() throws Exception {
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass qcSnp = new QueryClass(os.getModel().
                getClassDescriptorByName("SNP").getType());
        QueryField qfSnpId = new QueryField(qcSnp, "identifier");
        q.addFrom(qcSnp);
        q.addToSelect(qfSnpId);

        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();

        while (iterator.hasNext()) {
            ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
            snpIdSet.add(rr.get(0));
        }
    }

    /**
     * DB read SNPFunction column "name".
     *
     * @throws Exception
     */
    private void getSnpFunctionNames() throws Exception {
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass qcSnpFunction = new QueryClass(os.getModel().
                getClassDescriptorByName("SNPFunction").getType());

        q.addFrom(qcSnpFunction);
        q.addToSelect(qcSnpFunction);

        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();

        while (iterator.hasNext()) {
            ResultsRow<InterMineObject> rr = (ResultsRow<InterMineObject>) iterator.next();
            InterMineObject p = rr.get(0);

            String name = (String) p.getFieldValue("name");
            if (name != null) {
                snpFunctionNameSet.add(name);
            }
        }
    }

    /**
     * DB read UMLSTerm column "identifier".
     *
     * @throws Exception
     */
    private void getUMLSTerm() throws Exception {
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass qcUMLSTerm = new QueryClass(os.getModel().
                getClassDescriptorByName("UMLSTerm").getType());

        q.addFrom(qcUMLSTerm);
        q.addToSelect(qcUMLSTerm);

        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();

        while (iterator.hasNext()) {
            ResultsRow<InterMineObject> rr = (ResultsRow<InterMineObject>) iterator.next();
            InterMineObject p = rr.get(0);

            String identifier = (String) p.getFieldValue("identifier");
            if (identifier != null) {
                umlsTermSet.add(identifier);
            }
        }
    }

    private String getOrCreateSnpFunction(ResultSet response) throws Exception {
        String mutype = response.getString("mutype");

        if (StringUtils.isEmpty(mutype)) {
            return "";
        }

        // mapping mutype -> snpfunction name.
        String snpFunctionName = mutypeToSnpFunctionNames.get(mutype);
        if (snpFunctionName == null) {
            // not mapping. use mutype.
            snpFunctionName = mutype;
        }

        String ret = snpFunctionNameMap.get(snpFunctionName);
        if (ret == null) {
            // create item.
            Item item = createItem("SNPFunction");
            item.setAttribute("name", snpFunctionName);
            store(item);
            ret = item.getIdentifier();
            snpFunctionNameMap.put(snpFunctionName, item.getIdentifier());
        }
        return ret;
    }

    private String getGene(String primaryIdentifier) throws ObjectStoreException {
        if (StringUtils.isEmpty(primaryIdentifier)) {
            return "";
        }
        String ret = geneMap.get(primaryIdentifier);

        if (ret == null) {
            Item item = createItem("Gene");
            item.setAttribute("primaryIdentifier", primaryIdentifier);
            item.setAttribute("ncbiGeneId", primaryIdentifier);
            store(item);
            ret = item.getIdentifier();
            geneMap.put(primaryIdentifier, ret);
        }
        return ret;
    }

    private String getVariationAnnotation(String variationId, String geneRef, String functionRef, String snpRef) throws ObjectStoreException {
        String ret = variationAnnotationMap.get(variationId);
        if (ret == null) {
            Item item = createItem("VariationAnnotation");
            item.setAttribute("identifier", variationId);
            if (!StringUtils.isEmpty(geneRef)) {
                item.setReference("gene", geneRef);
            }
            if (!StringUtils.isEmpty(functionRef)) {
                item.setReference("function", functionRef);
            }
            if (!StringUtils.isEmpty(snpRef)) {
                item.setReference("snp", snpRef);
            }
            store(item);
            ret = item.getIdentifier();
            variationAnnotationMap.put(variationId, ret);
        }
        return ret;
    }

    private String getSNPReference(ResultSet response, String snpFunctionRef, String variationAnnotationRef) throws Exception {
        String mrnaAcc = combineString(response.getString("refCORE"), response.getString("refVER"), "."); // TODO: 文字列結合の方法?
        String mrnaPos = response.getString("cSTART");

        String orientation = ""; // TODO: データの作り方?
        String allele = combineString(response.getString("wildBASE"), response.getString("mutBASE"), " -> "); // TODO: 文字列結合の方法?
        String codon = ""; // TODO: データの作り方?
        String proteinAcc = combineString(response.getString("protCORE"), response.getString("protVER"), "."); // TODO: 文字列結合の方法?
        int aaPos = response.getInt("codon");
        String residue = combineString(response.getString("wildAMINO"), response.getString("mutAMINO"), " -> "); // TODO: 文字列結合の方法?
        String funcRef = snpFunctionRef;
        String vaItemRef = variationAnnotationRef;

        return createSNPReference(mrnaAcc, mrnaPos, orientation, allele, codon, proteinAcc, aaPos, residue, funcRef, vaItemRef);
    }

    private String createSNPReference(String mrnaAcc, String mrnaPos, String orientation,
                                      String allele, String codon, String proteinAcc, int aaPos, String residue,
                                      String funcRef, String vaItemRef) throws ObjectStoreException {
        Item item = createItem("SNPReference");
        if (!StringUtils.isEmpty(mrnaAcc)) {
            item.setAttribute("mrnaAccession", mrnaAcc);
        }
        if (!StringUtils.isEmpty(mrnaPos)) {
            item.setAttribute("mrnaPosition", mrnaPos);
        }
        if (!StringUtils.isEmpty(orientation)) {
            item.setAttribute("orientation", orientation);
        }
        if (!StringUtils.isEmpty(allele)) {
            item.setAttribute("mrnaAllele", allele);
        }
        if (!StringUtils.isEmpty(codon)) {
            item.setAttribute("mrnaCodon", codon);
        }
        if (!StringUtils.isEmpty(proteinAcc)) {
            item.setAttribute("proteinAccession", proteinAcc);
        }
        if (aaPos > 0) {
            item.setAttribute("proteinPosition", String.valueOf(aaPos));
        }
        if (!StringUtils.isEmpty(residue)) {
            item.setAttribute("residue", residue);
        }
        if (!StringUtils.isEmpty(funcRef)) {
            item.setReference("function", funcRef);
        }
        if (!StringUtils.isEmpty(vaItemRef)) {
            item.setReference("annotation", vaItemRef);
        }

        store(item);

        return item.getIdentifier();
    }

    private String combineString(String str1, String str2, String combineStr) {
        String str = "";
        if (!StringUtils.isEmpty(str1) && !StringUtils.isEmpty(str2)) {
            str = str1 + combineStr + str2;
        } else if (!StringUtils.isEmpty(str1) && StringUtils.isEmpty(str2)) {
            str = str1;
        } else if (StringUtils.isEmpty(str1) && !StringUtils.isEmpty(str2)) {
            str = str2;
        }

        return str;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDataSetTitle(String taxonId) {
        return DATASET_TITLE;
    }

    public void setOsAlias(String osAlias) {
        this.osAlias = osAlias;
    }
}

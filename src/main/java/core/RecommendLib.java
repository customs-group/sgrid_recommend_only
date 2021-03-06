package core;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import util.*;
import core.columnGroups.*;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 *
 * Created by edwardlol on 16/7/9.
 */
public class RecommendLib {
    //~ Static fields/initializers ---------------------------------------------

    private static RecommendLib instance = null;

    //~ Instance fields --------------------------------------------------------

    private final List<DefectSimple> defects = Lists.newArrayList(); // a list of all defects
    private final Map<String, Set<Integer>> invertedIndex = Maps.newHashMap(); // inverted index of the corpus
    private final List<String> featureTerms = Lists.newArrayList(); // the terms to make feature vectors

    private double threshold = 0.0d;
    private int keyWordBoundary = 255; // the maximun number of key words of each defect
    private int featureVectorBoundary = 1280; // the maximun length of feature vector

    //~ Constructors -----------------------------------------------------------

    private RecommendLib() {}

    //~ Methods ----------------------------------------------------------------

    /**
     * get the only instance of this class
     * @return the only instance of this class
     */
    public static RecommendLib getInstance() {
        if (instance == null) {
            instance = new RecommendLib();
        }
        return instance;
    }

    /**
     * init the lib from a given csv file
     * @param file name of the given file
     */
    public void initFromCSVFile(String file) {
        long startTime = System.currentTimeMillis();

        try (FileReader fileReader = new FileReader(file);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String line = bufferedReader.readLine(); // first line is column names
            line = bufferedReader.readLine();
            while (line != null) {
                String[] contents = line.split(",");
                String defectApperance = Util.readStringWithNull(contents[15]);
                String defectDescription = Util.readStringWithNull(contents[16]);
                String recommendation = Util.readStringWithNull(contents[20]);
                Document _defectDescription = new Document(defectDescription);

                Details details = new Details.DetailsBuilder(defectApperance, _defectDescription)
                        .recommendation(recommendation).createDetails();

                DefectSimple defect = new DefectSimple.Builder().details(details).build();
                this.defects.add(defect);
                line = bufferedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        long finishTime = System.currentTimeMillis();
        System.out.println("data reading finished in " + (finishTime - startTime) / 1000.0 + " seconds");
        this.train();
    }

    /**
     * init the lib from a DBMS
     * @param url the url of the DBMS
     * @param username username
     * @param password password
     */
    @SuppressWarnings("unused")
    public void initFromDB(String url, String username, String password) {
        JDBCUtil jdbcUtil = JDBCUtil.getInstance();
        jdbcUtil.dbms = JDBCUtil.DBMS.ORACLE;
        try (Connection con = jdbcUtil.getConnection(url, username, password);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ...")) {

            rs.next(); // first line is column names
            while (rs.next()) {
                String defectApperance = rs.getString(15);
                String defectDescription = rs.getString(16);
                String recommendation = rs.getString(20);
                Document _defectDescription = new Document(defectDescription);

                Details details = new Details.DetailsBuilder(defectApperance, _defectDescription)
                        .recommendation(recommendation).createDetails();

                DefectSimple defect = new DefectSimple.Builder().details(details).build();
                this.defects.add(defect);
            }
        } catch (SQLException se) {
            System.out.println("数据库连接失败！");
            se.printStackTrace();
        }
    }

    /**
     * train the lib
     */
    private void train() {
        long startTime = System.currentTimeMillis();
        // seperate the description and count the terms of each defect
        this.defects.forEach(defect -> {
            List<String> seperatedDescription = Util.seperate(defect.getDescription().toString());
            defect.getDescription().countTerms(seperatedDescription);
        });
        // calculate the inverted index of the corpus
        for (int i = 0; i < this.defects.size(); i++) {
            for (String term : this.defects.get(i).getDescription().getTermsCount().keySet()) {
                Set<Integer> indexes = this.invertedIndex.containsKey(term) ? this.invertedIndex.get(term) : Sets.newHashSet();
                indexes.add(i);
                this.invertedIndex.put(term, indexes);
            }
        }
        // calculate the TF-IDF of each term in each defect
        this.defects.forEach(defect ->
                defect.getDescription().calculateTF_IDF(this.defects.size(), this.invertedIndex));
        // calculate the key words of each defect's description
        Set<String> globalKeyWords = Sets.newHashSet();
        this.defects.forEach(defect ->
                globalKeyWords.addAll(defect.getDescription().calculateKeyWords(this.keyWordBoundary)));
        this.calculateFeatureTerms(globalKeyWords);
        // calculate the feature vector of each defect in the defect list
        this.defects.forEach(defect ->
                defect.getDescription().calculateFeatureVector(this.featureTerms));

        long finishTime = System.currentTimeMillis();
        System.out.println("training finished in " + (finishTime - startTime) / 1000.0 + " seconds");
    }

    /**
     * recommend a list contains only one solutions according
     * to the input description
     * for debug usage
     * @param description the input description of recommendDebug
     * @return a list containing the most similar defect and its solution
     */
    @SuppressWarnings("unused")
    public List<DefectSimple> recommendDebug(String description) {
        return recommendDebug(description, 1);
    }

    /**
     * recommend a list of solutions according to the input description
     * for debug usage
     * @param description the input description of recommendDebug
     * @param displayBoundary the boundary to display recommendDebug solutions
     * @return a list of similar defects and their solutions
     */
    public List<DefectSimple> recommendDebug(String description, int displayBoundary) {
        assert this.defects.size() != 0;

        long startTime = System.currentTimeMillis();

        Document _defectDescription = new Document(description);
        _defectDescription.countTerms(Util.seperate(_defectDescription.toString()));
        System.out.println("seperation result: " + _defectDescription.checkSep());
        _defectDescription.calculateTF_IDF(this.defects.size(), this.invertedIndex);
        _defectDescription.calculateKeyWords(this.keyWordBoundary);
        _defectDescription.calculateFeatureVector(this.featureTerms);

        double[] newFeatureVector = _defectDescription.getFeatureVector();
        Map<DefectSimple, Double> defectMap = Maps.newHashMap();
        for (DefectSimple oldDefect : this.defects) {
            double[] oldFeatureVector = oldDefect.getDescription().getFeatureVector();
            double similarity = 0.0;
            for (int i = 0; i < newFeatureVector.length; i++) {
                similarity += newFeatureVector[i] * oldFeatureVector[i];
            }
            if (similarity > this.threshold) {
                defectMap.put(oldDefect, similarity);
            }
        }

        List<Map.Entry<DefectSimple, Double>> resultMapList = Lists.newArrayList(defectMap.entrySet());
        Collections.sort(resultMapList, (entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        List<DefectSimple> resultList = Lists.newArrayList();
        int realBoundary = Math.min(displayBoundary, resultMapList.size());
        for (int i = 0; i < realBoundary; i++) {
            DefectSimple defect = resultMapList.get(i).getKey();
            resultList.add(defect);
            System.out.println("recommendDebug solution " + (i + 1) + ", similarity: " + resultMapList.get(i).getValue());
            System.out.println(defect.getDetails().toString());
        }
        long finishTime = System.currentTimeMillis();
        System.out.println("searching done in " + (finishTime - startTime) / 1000.0 + " seconds");
        return resultList;
    }

    /**
     * recommend a list containing only one solutions
     * according to the input description
     * @param description the input description of recommendDebug
     * @return a list containing only the most similar defect's solution
     */
    @SuppressWarnings("unused")
    public List<String> recommend(String description) {
        return recommend(description, 1, false);
    }

    /**
     * recommend a list of strings containing solutions
     * according to the input description
     * @param description the input description of recommendDebug
     * @param displayBoundary the boundary to display recommendDebug solutions
     * @param dedup whether to delete the duplicated output
     * @return a list of strings containing solutions
     */
    public List<String> recommend(String description, int displayBoundary, boolean dedup) {
        assert this.defects.size() != 0 : "recommend lib not properly initialized";

        List<String> result = Lists.newArrayList();

        Document _defectDescription = new Document(description);
        _defectDescription.countTerms(Util.seperate(_defectDescription.toString()));
        _defectDescription.calculateTF_IDF(this.defects.size(), this.invertedIndex);
        _defectDescription.calculateKeyWords(this.keyWordBoundary);
        _defectDescription.calculateFeatureVector(this.featureTerms);
        double[] newFeatureVector = _defectDescription.getFeatureVector();

        Map<DefectSimple, Double> defectMap = Maps.newHashMap();
        for (DefectSimple oldDefect : this.defects) {
            double[] oldFeatureVector = oldDefect.getDescription().getFeatureVector();
            double similarity = 0.0;
            for (int i = 0; i < newFeatureVector.length; i++) {
                similarity += newFeatureVector[i] * oldFeatureVector[i];
            }
            if (similarity > this.threshold) {
                defectMap.put(oldDefect, similarity);
            }
        }
        List<Map.Entry<DefectSimple, Double>> resultMapList = Lists.newArrayList(defectMap.entrySet());
        Collections.sort(resultMapList, (entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

        Set<String> dedupSet = Sets.newHashSet();
        Iterator<Map.Entry<DefectSimple, Double>> iterator = resultMapList.iterator();
        int i = 0;
        int realBoundary = Math.min(displayBoundary, resultMapList.size());
        while (iterator.hasNext() && i < realBoundary) {
            DefectSimple defect = iterator.next().getKey();
            if (dedup) {
                if (!dedupSet.contains(defect.getRecommendation())) {
                    dedupSet.add(defect.getRecommendation());
                    result.add(defect.getRecommendation());
                    i++;
                }
            } else {
                result.add(defect.getRecommendation());
                i++;
            }
        }
        return result;
    }

    /**
     * calculate the feature terms of the corpus
     * @param globalKeyWords a set of all key words of every defect
     */
    private void calculateFeatureTerms(Set<String> globalKeyWords) {
        // setup a list of global keywords and their count
        Map<String, Integer> globalKeyWordsCount = Maps.newHashMap();
        globalKeyWords.forEach(keyWord -> globalKeyWordsCount.put(keyWord, this.invertedIndex.get(keyWord).size()));
        List<Map.Entry<String, Integer>> globalKeyWordsCountList = Lists.newArrayList(globalKeyWordsCount.entrySet());
        Collections.sort(globalKeyWordsCountList, (entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        // set the boundary of feature terms
        int realBoundary = Math.min(this.featureVectorBoundary, globalKeyWordsCountList.size());
        globalKeyWordsCountList = globalKeyWordsCountList.subList(0, realBoundary);
        // set the feature terms
        for (Map.Entry<String, Integer> entry : globalKeyWordsCountList) {
            String term = entry.getKey();
            this.featureTerms.add(term);
        }
    }

    /**
     * sort the terms in the inverted index according to the number of
     * articles containing the term
     * @return a list of sorted inverted index in descending order
     */
    @SuppressWarnings("unused")
    private List<Map.Entry<String, Set<Integer>>> sortInvertedIndex() {
        List<Map.Entry<String, Set<Integer>>> invertedIndexList = Lists.newArrayList(this.invertedIndex.entrySet());
        Collections.sort(invertedIndexList, (entry1, entry2) -> {
            if (entry2.getValue().size() == entry1.getValue().size()) {
                return 0;
            } else if (entry2.getValue().size() > entry1.getValue().size()) {
                return 1;
            } else {
                return -1;
            }
        });
        return invertedIndexList;
    }

    //~ Setter methods ---------------------------------------------------------

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
    public void setKeyWordBoundary(int keyWordBoundary) {
        this.keyWordBoundary = keyWordBoundary;
    }
    public void setFeatureVectorBoundary(int featureVectorBoundary) {
        this.featureVectorBoundary = featureVectorBoundary;
    }
}

// End RecommendLib.java

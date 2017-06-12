package org.insightcentre.richcontext;

import com.opencsv.CSVWriter;
import net.recommenders.rival.core.*;
import net.recommenders.rival.evaluation.metric.error.MAE;
import net.recommenders.rival.evaluation.metric.error.RMSE;
import net.recommenders.rival.evaluation.metric.ranking.NDCG;
import net.recommenders.rival.evaluation.metric.ranking.Precision;
import net.recommenders.rival.evaluation.metric.ranking.Recall;
import net.recommenders.rival.evaluation.strategy.AllItems;
import net.recommenders.rival.evaluation.strategy.EvaluationStrategy;
import net.recommenders.rival.evaluation.strategy.RelPlusN;
import net.recommenders.rival.evaluation.strategy.TestItems;
import net.recommenders.rival.evaluation.strategy.UserTest;
import net.recommenders.rival.split.splitter.CrossValidationSplitter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by fpena on 23/03/2017.
 */
public class RatingContextEvaluator {

    /**
     * Default number of folds.
     */
    public static final int NUM_FOLDS = 5;
    /**
     * Default neighbohood size.
     */
    public static final int NEIGHBOURHOOD_SIZE = 50;
    /**
     * Default cutoff for evaluation metrics.
     */
    public static final int AT = 10;
    /**
     * Default relevance threshold.
     */
    public static final double RELEVANCE_THRESHOLD = 5.0;
    /**
     * Default seed.
     */
    public static final long SEED = 2048L;

    public static final Strategy STRATEGY = Strategy.TEST_ITEMS;


    private Map<String, Review> reviewsMap;
    private String ratingsFolderPath;
    private String jsonRatingsFile;
    private int topN;
    private int numUsers;
    private int numItems;


    private enum Strategy {
        ALL_ITEMS,
        REL_PLUS_N,
        TEST_ITEMS,
        TRAIN_ITEMS,
        USER_TEST
    }



    public RatingContextEvaluator(String jsonRatingsFile) {
        this.jsonRatingsFile = jsonRatingsFile;

        init();
    }

    public static void main(final String[] args) throws IOException, InterruptedException {

        long startTime = System.currentTimeMillis();

//        String folder = "data/rich-context/";
//        String modelPath = folder + "model/";
//        String recPath = folder + "recommendations/";
        int nFolds = NUM_FOLDS;
//        String dataFile = folder + "yelp_hotel.json";
//        String algorithm = "GlobalAvg";
//        String algorithm = "UserAvg";
//        String algorithm = "ItemAvg";
//        String algorithm = "UserItemAvg";

//        String workingPath;

//        workingPath = "/Users/fpena/UCC/Thesis/datasets/context/stuff/cache_context/" +
//                "carskit/yelp_hotel_carskit_ratings_ensemble_numtopics-30" +
//                "_iterations-100_passes-10_targetreview-specific_" +
//                "normalized_ck-no_context_lang-en_bow-NN_" +
//                "document_level-review_targettype-context_" +
//                "min_item_reviews-10/";

        String cacheFolder =
                "/Users/fpena/UCC/Thesis/datasets/context/stuff/cache_context/";
        String jsonFile;
//        jsonFile = cacheFolder + "yelp_hotel_recsys_contextual_records_ensemble_" +
//                "numtopics-10_iterations-100_passes-10_targetreview-specific_" +
//                "normalized_lang-en_bow-NN_document_level-review_" +
//                "targettype-context_min_item_reviews-10.json";
//        jsonFile = cacheFolder + "yelp_restaurant_recsys_contextual_records_ensemble_" +
//                "numtopics-50_iterations-100_passes-10_targetreview-specific_" +
//                "normalized_lang-en_bow-NN_document_level-review_" +
//                "targettype-context_min_item_reviews-10.json";

//        jsonFile = cacheFolder + "fourcity_hotel_recsys_contextual_records_ensemble_" +
//                "numtopics-10_iterations-100_passes-10_targetreview-specific_" +
//                "normalized_lang-en_bow-NN_document_level-review_" +
//                "targettype-context_min_item_reviews-10.json";

        jsonFile = cacheFolder + "yelp_restaurant_recsys_formatted_context_records_ensemble_" +
                "numtopics-10_iterations-100_passes-10_targetreview-specific_" +
                "normalized_contextformat-no_context_lang-en_bow-NN_" +
                "document_level-review_targettype-context_" +
                "min_item_reviews-10.json";

        // Non-contextual files
//        jsonFile = cacheFolder + "yelp_hotel_recsys_contextual_records_" +
//                "lang-en_bow-NN_document_level-review_targettype-context_" +
//                "min_item_reviews-10.json";



//        workingPath = "/Users/fpena/tmp/CARSKit/context-aware_data_sets/yelp_hotel/";

        RatingContextEvaluator evaluator = new RatingContextEvaluator(jsonFile);
        evaluator.prepareSplits(nFolds);
//        evaluator.transformSplitsToCarskit(NUM_FOLDS);
        evaluator.transformSplitsToLibfm(NUM_FOLDS);
//        evaluator.parseRecommendationResultsLibfm(NUM_FOLDS);
//        evaluator.prepareStrategy(NUM_FOLDS, "libfm");
//        evaluator.evaluate(NUM_FOLDS, "libfm");

        String[] algorithms = {
                "GlobalAvg",
                "UserAvg",
                "ItemAvg",
                "UserItemAvg",
                "SlopeOne",
                "PMF",
                "BPMF",
                "BiasedMF",
                "NMF",
                "CAMF_CI", "CAMF_CU",
                "CAMF_CUCI",
//                "SLIM",
//                "BPR",
//                "LRMF",
//                "CSLIM_C", "CSLIM_CI",
//                "CSLIM_CU",
        };

//        for (String algorithm : algorithms) {
//            evaluator.postProcess(NUM_FOLDS, algorithm);
//        }


//        RatingContextEvaluator evaluator = new RatingContextEvaluator("GlobalAvg", workingPath);

//        String fileName = getRecommendationsFileName(workingPath, "GlobalAvg", 1, -10);
//        System.out.println(fileName);

        long endTime   = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        System.out.println("Running time: " + (totalTime/1000));
    }


    private void init() {

        File jsonFile =  new File(jsonRatingsFile);
        String jsonFileName = jsonFile.getName();
        String jsonFileParentFolder = jsonFile.getParent();
        String rivalFolderPath = jsonFileParentFolder + "/rival/";

        File rivalDir = new File(rivalFolderPath);
        if (!rivalDir.exists()) {
            if (!rivalDir.mkdir()) {
                System.err.println("Directory " + rivalDir + " could not be created");
                return;
            }
        }

        // We strip the extension of the file name to create a new folder with
        // an unique name
        if (jsonFileName.indexOf(".") > 0) {
            jsonFileName = jsonFileName.substring(0, jsonFileName.lastIndexOf("."));
        }

        ratingsFolderPath = rivalFolderPath + jsonFileName + "/";

        File ratingsDir = new File(ratingsFolderPath);
        if (!ratingsDir.exists()) {
            if (!ratingsDir.mkdir()) {
                System.err.println("Directory " + ratingsDir + " could not be created");
                return;
            }
        }

        System.out.println("File name: " + jsonFileName);
        System.out.println("Parent folder: " + jsonFileParentFolder);
        System.out.println("Ratings folder: " + ratingsFolderPath);
    }

    /**
     * Downloads a dataset and stores the splits generated from it.
     *
     * @param nFolds number of folds
     */
    public void prepareSplits(final int nFolds) {

        String dataFile = jsonRatingsFile;
        boolean perUser = false;
        long seed = SEED;
        JsonParser parser = new JsonParser();

        DataModelIF<Long, Long> data = null;
        try {
            data = parser.parseData(new File(dataFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Build reviews map
        this.reviewsMap = new HashMap<>();
        Set<Long> itemsSet = new HashSet<>();
        Set<Long> usersSet = new HashSet<>();
        for (Review review : parser.getReviews()) {
            reviewsMap.put(review.getUser_item_key(), review);
            itemsSet.add(review.getItemId());
            usersSet.add(review.getUserId());
        }
        this.numItems = itemsSet.size();
        this.numUsers = usersSet.size();
        System.out.println("Num items: " + this.numItems);
        System.out.println("Num users: " + this.numUsers);


        DataModelIF<Long, Long>[] splits =
                new CrossValidationSplitter<Long, Long>(
                        nFolds, perUser, seed).split(data);
        File dir = new File(ratingsFolderPath);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                System.err.println("Directory " + dir + " could not be created");
                return;
            }
        }
        for (int i = 0; i < splits.length / 2; i++) {

            String foldPath = ratingsFolderPath + "fold_" + i + "/";
            File foldDir = new File(foldPath);
            if (!foldDir.exists()) {
                if (!foldDir.mkdir()) {
                    System.err.println("Directory " + foldDir + " could not be created");
                    return;
                }
            }

            DataModelIF<Long, Long> training = splits[2 * i];
            DataModelIF<Long, Long> test = splits[2 * i + 1];
            String trainingFile = foldPath + "train.csv";
            String testFile = foldPath + "test.csv";
            boolean overwrite = true;
            try {
                DataModelUtils.saveDataModel(training, trainingFile, overwrite, "\t");
                DataModelUtils.saveDataModel(test, testFile, overwrite, "\t");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }


    public void transformSplitToCarskit(int fold) throws IOException {

        String foldPath = ratingsFolderPath + "fold_" + fold + "/";
        String trainFile = foldPath + "train.csv";
        String testFile = foldPath + "test.csv";
        List<Review> incompleteTrainReviews = ReviewCsvDao.readCsvFile(trainFile);
        List<Review> incompleteTestReviews = ReviewCsvDao.readCsvFile(testFile);

        List<Review> completeTrainReviews = new ArrayList<>();
        List<Review> completeTestReviews = new ArrayList<>();

        for (Review review : incompleteTrainReviews) {
            Review completeReview = reviewsMap.get(review.getUser_item_key());
            completeTrainReviews.add(completeReview);
        }
        for (Review review : incompleteTestReviews) {
            Review completeReview = reviewsMap.get(review.getUser_item_key());
            completeTestReviews.add(completeReview);
        }

        String carskitTrainFile = foldPath + "carskit_train.csv";
        String carskitTestFile = foldPath + "carskit_test.csv";

//        CarskitExporter.exportWithoutContext(
//                completeTrainReviews, carskitTrainFile);
//        CarskitExporter.exportWithoutContext(
//                completeTestReviews, carskitTestFile);
        CarskitExporter.exportWithContext(
                completeTrainReviews, carskitTrainFile);
        CarskitExporter.exportWithContext(
                completeTestReviews, carskitTestFile);

        String workspacePath = foldPath + "CARSKit.Workspace/";
        File workspaceDir = new File(workspacePath);
        if (!workspaceDir.exists()) {
            if (!workspaceDir.mkdir()) {
                System.err.println("Directory " + workspaceDir + " could not be created");
                return;
            }
        }

        String ratingsBinaryPath = workspacePath + "ratings_binary.txt";

        Files.copy(
                new File(carskitTrainFile).toPath(),
                new File(ratingsBinaryPath).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }


    public void transformSplitsToCarskit(int numFolds) throws IOException {

        for (int fold = 0; fold < numFolds; fold++) {
            transformSplitToCarskit(fold);
        }
    }


    public void transformSplitToLibfm(int fold) throws IOException {

        String foldPath = ratingsFolderPath + "fold_" + fold + "/";
        String trainFile = foldPath + "train.csv";
        String testFile = foldPath + "test.csv";
        List<Review> incompleteTrainReviews = ReviewCsvDao.readCsvFile(trainFile);
        List<Review> incompleteTestReviews = ReviewCsvDao.readCsvFile(testFile);

        List<Review> completeTrainReviews = new ArrayList<>();
        List<Review> completeTestReviews = new ArrayList<>();

        for (Review review : incompleteTrainReviews) {
            Review completeReview = reviewsMap.get(review.getUser_item_key());
            completeTrainReviews.add(completeReview);
        }
        for (Review review : incompleteTestReviews) {
            Review completeReview = reviewsMap.get(review.getUser_item_key());
            completeTestReviews.add(completeReview);
        }

        String libfmTrainFile = foldPath + "libfm_train.libfm";
        String libfmTestFile = foldPath + "libfm_test.libfm";
//        String libfmPredictionsFile = foldPath + "libfm_preds.libfm";

        Map<String, Integer> oneHotIdMap =
                LibfmExporter.getOneHot(reviewsMap.values());

        LibfmExporter.exportRecommendations(
                completeTrainReviews, libfmTrainFile, oneHotIdMap);
        LibfmExporter.exportRecommendations(
                completeTestReviews, libfmTestFile, oneHotIdMap);
//        LibfmExporter.exportRankingPredictionsFile(
//                completeTrainReviews, completeTestReviews, libfmPredictionsFile, oneHotIdMap);
    }


    public void transformSplitsToLibfm(int numFolds) throws IOException {

        for (int fold = 0; fold < numFolds; fold++) {
            transformSplitToLibfm(fold);
        }
    }


    public void parseRecommendationResults(int numFolds, String algorithm) throws IOException, InterruptedException {

        System.out.println("Parse Recommendation Results");

        for (int fold = 0; fold < numFolds; fold++) {


            // Execute the recommender

//            String configFile = ratingsFolderPath + algorithm + "_" + fold + ".conf";
//            CarskitCaller.run(configFile);


            // Collect the results from the recommender
            String recommendationsFile = getRecommendationsFileName(
                    ratingsFolderPath, algorithm, fold, AT);

            List<Review> recommendations = (AT < 1) ?
                    CarskitResultsParser.parseRatingResults(recommendationsFile) :
                    CarskitResultsParser.parseRankingResults(recommendationsFile);

            String rivalRecommendationsFile =
                    ratingsFolderPath + "fold_" + fold + "/recs_" + algorithm + ".csv";
            System.out.println("Recommendations file name: " + rivalRecommendationsFile);
            CarskitExporter.exportRecommendationsToCsv(
                    recommendations, rivalRecommendationsFile);
        }
    }


    public void parseRecommendationResultsLibfm(int numFolds) throws IOException, InterruptedException {

        System.out.println("Parse Recommendation Results");

        for (int fold = 0; fold < numFolds; fold++) {


            // Execute the recommender

//            String configFile = ratingsFolderPath + algorithm + "_" + fold + ".conf";
//            CarskitCaller.run(configFile);


            // Collect the results from the recommender
            String foldPath = ratingsFolderPath + "fold_" + fold + "/";
            String testFile = foldPath + "test.csv";
            String libfmResultsFile = foldPath + "libfm_predictions.txt";

            List<Review> recommendations =
                    LibfmResultsParser.parseRatingResults(testFile, libfmResultsFile);

            String rivalRecommendationsFile =
                    ratingsFolderPath + "fold_" + fold + "/recs_libfm" + ".csv";
            System.out.println("Recommendations file name: " + rivalRecommendationsFile);
            CarskitExporter.exportRecommendationsToCsv(
                    recommendations, rivalRecommendationsFile);
        }
    }


    public void prepareStrategy(final int nFolds, String algorithm) {

        System.out.println("Prepare Strategy");

        for (int i = 0; i < nFolds; i++) {

            String foldPath = ratingsFolderPath + "fold_" + i + "/";
            File trainingFile = new File(foldPath + "train.csv");
            File testFile = new File(foldPath + "test.csv");
            File recFile = new File(foldPath + "recs_" + algorithm + ".csv");
            DataModelIF<Long, Long> trainingModel;
            DataModelIF<Long, Long> testModel;
            DataModelIF<Long, Long> recModel;
            try {
                trainingModel = new SimpleParser().parseData(trainingFile);
                testModel = new SimpleParser().parseData(testFile);
                recModel = new SimpleParser().parseData(recFile);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            Double threshold = RELEVANCE_THRESHOLD;
//            String strategyClassName = "net.recommenders.rival.evaluation.strategy.UserTest";
            EvaluationStrategy<Long, Long> evaluationStrategy = null;

            switch (STRATEGY) {
                case ALL_ITEMS: evaluationStrategy =
                        new AllItems((DataModel)trainingModel, (DataModel)testModel, threshold);
                    break;
                case REL_PLUS_N: evaluationStrategy =
                        new RelPlusN(trainingModel, testModel, 1000, threshold, SEED);
                    break;
                case TEST_ITEMS: evaluationStrategy =
                        new TestItems((DataModel)trainingModel, (DataModel)testModel, threshold);
                case TRAIN_ITEMS: evaluationStrategy =
                        new TestItems((DataModel)trainingModel, (DataModel)testModel, threshold);
                    break;
                case USER_TEST: evaluationStrategy =
                        new UserTest(trainingModel, testModel, threshold);
                    break;
                default: throw new UnsupportedOperationException("The requested Evaluation Strategy doesn't exist");
            }


//            EvaluationStrategy<Long, Long> strategy =
//                    new RelPlusN(trainingModel, testModel, 100, threshold, SEED);
//            EvaluationStrategy<Long, Long> strategy =
//                    new TestItems((DataModel)trainingModel, (DataModel)testModel, threshold);

            DataModelIF<Long, Long> modelToEval = DataModelFactory.getDefaultModel();
            for (Long user : recModel.getUsers()) {
//                assert strategy != null;
                for (Long item : evaluationStrategy.getCandidateItemsToRank(user)) {
                    if (recModel.getUserItemPreferences().get(user).containsKey(item)) {
                        modelToEval.addPreference(user, item, recModel.getUserItemPreferences().get(user).get(item));
                    }
                }
            }
            try {
                DataModelUtils.saveDataModel(modelToEval, foldPath + "strategymodel_" + algorithm + "_" + STRATEGY.toString() + ".csv", true, "\t");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Evaluates the recommendations generated in previous steps.
     *
     * @param nFolds number of folds
     */
    public Map<String, String> evaluate(final int nFolds, String algorithm) {

        System.out.println("Evaluate");

        double ndcgRes = 0.0;
        double recallRes = 0.0;
        double precisionRes = 0.0;
        double rmseRes = 0.0;
        double maeRes = 0.0;
        Map<String, String> results =  new HashMap<>();
        for (int i = 0; i < nFolds; i++) {
            String foldPath = ratingsFolderPath + "fold_" + i + "/";
            File testFile = new File(foldPath + "test.csv");
            File strategyFile = new File(foldPath + "strategymodel_" + algorithm + "_" + STRATEGY.toString() + ".csv");
            DataModelIF<Long, Long> testModel = null;
            DataModelIF<Long, Long> recModel = null;
            try {
                testModel = new SimpleParser().parseData(testFile);
                recModel = new SimpleParser().parseData(strategyFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            NDCG<Long, Long> ndcg = new NDCG<>(recModel, testModel, new int[]{AT});
            ndcg.compute();
            ndcgRes += ndcg.getValueAt(AT);

            Recall<Long, Long> recall = new Recall<>(recModel, testModel, RELEVANCE_THRESHOLD, new int[]{AT});
            recall.compute();
            recallRes += recall.getValueAt(AT);

            RMSE<Long, Long> rmse = new RMSE<>(recModel, testModel);
            rmse.compute();
            rmseRes += rmse.getValue();

            MAE<Long, Long> mae = new MAE<>(recModel, testModel);
            mae.compute();
            maeRes += mae.getValue();

            Precision<Long, Long> precision = new Precision<>(recModel, testModel, RELEVANCE_THRESHOLD, new int[]{AT});
            precision.compute();
            precisionRes += precision.getValueAt(AT);
        }

        results.put("Algorithm", algorithm);
        results.put("Strategy", STRATEGY.toString());
        results.put("NDCG@" + AT, String.valueOf(ndcgRes / nFolds));
        results.put("Precision@" + AT, String.valueOf(precisionRes / nFolds));
        results.put("Recall@" + AT, String.valueOf(recallRes / nFolds));
        results.put("RMSE", String.valueOf(rmseRes / nFolds));
        results.put("MAE", String.valueOf(maeRes / nFolds));

        System.out.println("NDCG@" + AT + ": " + ndcgRes / nFolds);
        System.out.println("Precision@" + AT + ": " + precisionRes / nFolds);
        System.out.println("Recall@" + AT + ": " + recallRes / nFolds);
        System.out.println("RMSE: " + rmseRes / nFolds);
        System.out.println("MAE: " + maeRes / nFolds);
//        System.out.println("P@" + AT + ": " + precisionRes / nFolds);

        return results;
    }


    public void postProcess(int numFolds, String algorithm)
            throws IOException, InterruptedException {

        List<Map<String, String>> resultsList = new ArrayList<>();

//        RatingContextEvaluator evaluator = new RatingContextEvaluator(jsonFile);
        parseRecommendationResults(numFolds, algorithm);
        prepareStrategy(numFolds, algorithm);
        resultsList.add(evaluate(numFolds, algorithm));

        String[] headers = {
                "Algorithm",
                "Strategy",
                "NDCG@" + AT,
                "Precision@" + AT,
                "Recall@" + AT,
                "RMSE",
                "MAE"
        };

        File resultsFile = new File("/Users/fpena/tmp/rival_yelp_restaurant_predefined_context_results.csv");
        boolean fileExists = resultsFile.exists();
        CSVWriter writer = new CSVWriter(
                new FileWriter(resultsFile, true),
                ',', CSVWriter.NO_QUOTE_CHARACTER);

        if (!fileExists) {
            writer.writeNext(headers);
        }

        for (Map<String, String> results : resultsList) {
            String[] row = new String[headers.length];

            for (int i = 0; i < headers.length; i++) {
                row[i] = results.get(headers[i]);
            }
            writer.writeNext(row);
        }
        writer.close();
    }



    public String getRecommendationsFileName(
            String workingPath, String algorithm, int foldIndex, int topN) {

        String dataFile = jsonRatingsFile;
        JsonParser parser = new JsonParser();
        try {
            parser.parseData(new File(dataFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Set<Long> itemsSet = new HashSet<>();
        Set<Long> usersSet = new HashSet<>();
        for (Review review : parser.getReviews()) {
            itemsSet.add(review.getItemId());
            usersSet.add(review.getUserId());
        }
        this.numItems = itemsSet.size();
        this.numUsers = usersSet.size();
        System.out.println("Num items: " + this.numItems);
        System.out.println("Num users: " + this.numUsers);


        String filePath;
        String carskitWorkingPath =
                workingPath + "fold_" + foldIndex + "/CARSKit.Workspace/";
//        String foldInfo = foldIndex > 0 ? " fold [" + foldIndex + "]" : "";

        // This means that we are going to generate a rating prediction file name
        if (topN < 1) {
            filePath =
                    carskitWorkingPath + algorithm+ "-rating-predictions.txt";
        }
        else {
            filePath = carskitWorkingPath + String.format(
                    "%s-top-%d-items.txt", algorithm, this.numItems);
        }

        return filePath;
    }
}
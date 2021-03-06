import copy
import csv
import os
import random
from subprocess import call
import time
import cPickle as pickle
import uuid
import numpy
from etl import ETLUtils
from etl import libfm_converter
from evaluation import parameter_combinator
from evaluation import rmse_calculator
from evaluation.top_n_evaluator import TopNEvaluator
from topicmodeling.context import topic_model_creator
from topicmodeling.context.lda_based_context import LdaBasedContext
from topicmodeling.context.word_based_context import WordBasedContext
from tripadvisor.fourcity import extractor
from utils import utilities
from utils.constants import Constants

__author__ = 'fpena'


def build_headers(num_sense_groups):
    headers = [
        Constants.RATING_FIELD,
        Constants.USER_ID_FIELD,
        Constants.ITEM_ID_FIELD
    ]
    for i in range(num_sense_groups):
        group_id = 'sense_group' + str(i)
        headers.append(group_id)
    return headers


def create_user_item_map(records):
    user_ids = extractor.get_groupby_list(records, Constants.USER_ID_FIELD)
    user_item_map = {}
    user_count = 0

    for user_id in user_ids:
        user_records =\
            ETLUtils.filter_records(records, Constants.USER_ID_FIELD, [user_id])
        user_items =\
            extractor.get_groupby_list(user_records, Constants.ITEM_ID_FIELD)
        user_item_map[user_id] = user_items
        user_count += 1

        # print("user count %d" % user_count),
        print 'user count: {0}\r'.format(user_count),

    print

    return user_item_map


def run_libfm(train_file, test_file, predictions_file, log_file):

    libfm_command = Constants.LIBFM_FOLDER + 'libFM'

    command = [
        libfm_command,
        '-task',
        'r',
        '-train',
        train_file,
        '-test',
        test_file,
        '-dim',
        '1,1,' + str(Constants.FM_NUM_FACTORS),
        '-out',
        predictions_file
    ]

    print(command)

    if Constants.LIBFM_SEED is not None:
        command.extend(['-seed', str(Constants.LIBFM_SEED)])

    f = open(log_file, "w")
    call(command, stdout=f)


def filter_reviews(records, reviews, review_type):
    print('filter: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

    if not review_type:
        return records, reviews

    filtered_records = []
    filtered_reviews = []

    for record, review in zip(records, reviews):
        if record[Constants.PREDICTED_CLASS_FIELD] == review_type:
            filtered_records.append(record)
            filtered_reviews.append(review)

    return filtered_records, filtered_reviews


class WordContextTopNRunner(object):

    def __init__(self):
        self.records = None
        self.reviews = None
        self.original_records = None
        self.original_reviews = None
        self.train_records = None
        self.train_reviews = None
        self.test_records = None
        self.test_reviews = None
        self.records_to_predict = None
        self.top_n_evaluator = None
        self.headers = None
        self.important_records = None
        self.important_reviews = None
        self.context_rich_topics = []
        self.sense_groups = []
        self.csv_train_file = None
        self.csv_test_file = None
        self.context_predictions_file = None
        self.context_train_file = None
        self.context_test_file = None
        self.context_log_file = None

    def clear(self):
        print('clear: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        # self.records = None
        self.train_records = None
        self.train_reviews = None
        self.test_records = None
        self.test_reviews = None
        self.records_to_predict = None
        self.top_n_evaluator = None
        self.headers = None
        self.important_records = None
        self.important_reviews = None
        self.context_rich_topics = []
        self.sense_groups = []

        os.remove(self.csv_train_file)
        os.remove(self.csv_test_file)
        os.remove(self.context_predictions_file)
        os.remove(self.context_train_file)
        os.remove(self.context_test_file)
        os.remove(self.context_log_file)

        self.csv_train_file = None
        self.csv_test_file = None
        self.context_predictions_file = None
        self.context_train_file = None
        self.context_test_file = None
        self.context_log_file = None

    def create_tmp_file_names(self):

        unique_id = uuid.uuid4().hex
        prefix = Constants.GENERATED_FOLDER + unique_id + '_' + \
            Constants.ITEM_TYPE
        # prefix = constants.GENERATED_FOLDER + constants.ITEM_TYPE

        print('unique id: %s' % unique_id)

        self.csv_train_file = prefix + '_train.csv'
        self.csv_test_file = prefix + '_test.csv'
        self.context_predictions_file = prefix + '_predictions.txt'
        self.context_train_file = self.csv_train_file + '.libfm'
        self.context_test_file = self.csv_test_file + '.libfm'
        self.context_log_file = prefix + '.log'

    def load(self):
        print('load: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))
        self.original_records = ETLUtils.load_json_file(Constants.RECORDS_FILE)
        with open(Constants.REVIEWS_FILE, 'rb') as read_file:
            self.original_reviews = pickle.load(read_file)
        print('num_records: %d' % len(self.original_records))

        for record, review in zip(self.original_records, self.original_reviews):
            review.id = record[Constants.REVIEW_ID_FIELD]
            review.rating = record[Constants.RATING_FIELD]

        if not os.path.exists(Constants.USER_ITEM_MAP_FILE):
            records = ETLUtils.load_json_file(Constants.RECORDS_FILE)
            user_item_map = create_user_item_map(records)
            with open(Constants.USER_ITEM_MAP_FILE, 'wb') as write_file:
                pickle.dump(user_item_map, write_file, pickle.HIGHEST_PROTOCOL)

    def shuffle(self):
        print('shuffle: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))
        # random.shuffle(self.records)
        shuffled_records = []
        shuffled_reviews = []
        index_shuffle = range(len(self.original_records))
        random.shuffle(index_shuffle)
        for i in index_shuffle:
            shuffled_records.append(self.original_records[i])
            shuffled_reviews.append(self.original_reviews[i])
        self.original_records = shuffled_records
        self.original_reviews = shuffled_reviews

    def export(self):
        print('export: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        with open(Constants.USER_ITEM_MAP_FILE, 'rb') as read_file:
            user_item_map = pickle.load(read_file)

        self.top_n_evaluator = TopNEvaluator(
            self.records, self.test_records, Constants.ITEM_TYPE, 10,
            Constants.TOPN_NUM_ITEMS)
        self.top_n_evaluator.initialize(user_item_map)
        self.records_to_predict = self.top_n_evaluator.get_records_to_predict()
        self.important_records = self.top_n_evaluator.important_records
        self.important_reviews = [
            review for review in self.test_reviews if review.rating == 5
        ]

    def train_word_model(self):
        print('train topic model: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))
        # lda_based_context = LdaBasedContext(self.train_records)
        # lda_based_context.get_context_rich_topics()
        # self.context_rich_topics = lda_based_context.context_rich_topics
        word_based_context = WordBasedContext(self.train_reviews)
        word_based_context.calculate_sense_group_ratios()
        self.sense_groups = word_based_context.sense_groups
        print('Trained LDA Model: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        return word_based_context

    def find_reviews_topics(self, word_based_context):
        print('find topics: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        # lda_based_context.find_contextual_topics(self.train_records)
        for record, review in zip(self.train_records, self.train_reviews):
            record[Constants.CONTEXT_WORDS_FIELD] =\
                word_based_context.calculate_word_context(review)

        for record, review in zip(self.important_records, self.important_reviews):
            record[Constants.CONTEXT_WORDS_FIELD] =\
                word_based_context.calculate_word_context(review)

        topics_map = {}
        for record in self.important_records:
            topics_map[record[Constants.REVIEW_ID_FIELD]] =\
                record[Constants.CONTEXT_WORDS_FIELD]

        for record in self.records_to_predict:
            word_distribution = topics_map[record[Constants.REVIEW_ID_FIELD]]
            record[Constants.CONTEXT_WORDS_FIELD] = word_distribution

        print('contextual test set size: %d' % len(self.records_to_predict))
        print('Exported contextual topics: %s' %
              time.strftime("%Y/%m/%d-%H:%M:%S"))

    def prepare(self):
        print('prepare: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        self.headers = build_headers(len(self.sense_groups))

        if Constants.USE_CONTEXT is True:
            for record in self.train_records:
                record.update(record[Constants.CONTEXT_WORDS_FIELD])

            for record in self.records_to_predict:
                record.update(record[Constants.CONTEXT_WORDS_FIELD])

            if Constants.FM_REVIEW_TYPE:
                self.train_records = ETLUtils.filter_records(
                    self.train_records, Constants.PREDICTED_CLASS_FIELD,
                    [Constants.FM_REVIEW_TYPE])

            # ETLUtils.drop_fields([Constants.TOPICS_FIELD], self.train_records)

        ETLUtils.keep_fields(self.headers, self.train_records)
        ETLUtils.keep_fields(self.headers, self.records_to_predict)

        ETLUtils.save_csv_file(
            self.csv_train_file, self.train_records, self.headers)
        ETLUtils.save_csv_file(
            self.csv_test_file, self.records_to_predict, self.headers)

        print('Exported CSV and JSON files: %s'
              % time.strftime("%Y/%m/%d-%H:%M:%S"))

        csv_files = [
            self.csv_train_file,
            self.csv_test_file
        ]

        print('num_cols', len(self.headers))

        libfm_converter.csv_to_libfm(
            csv_files, 0, [1, 2], [], ',', has_header=True,
            suffix='.libfm')

        print('Exported LibFM files: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

    def predict(self):
        print('predict: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        run_libfm(
            self.context_train_file, self.context_test_file,
            self.context_predictions_file, self.context_log_file)

    def evaluate(self):
        print('evaluate: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        predictions = rmse_calculator.read_targets_from_txt(
            self.context_predictions_file)
        self.top_n_evaluator.evaluate(predictions)
        recall = self.top_n_evaluator.recall

        print('Recall: %f' % recall)
        print('Specific recall: %f' % self.top_n_evaluator.specific_recall)
        print('Generic recall: %f' % self.top_n_evaluator.generic_recall)

        return recall

    def perform_cross_validation(self):

        Constants.print_properties()

        utilities.plant_seeds()

        total_recall = 0.0
        total_specific_recall = 0.0
        total_generic_recall = 0.0
        total_cycle_time = 0.0
        num_cycles = Constants.NUM_CYCLES
        num_folds = Constants.CROSS_VALIDATION_NUM_FOLDS
        total_iterations = num_cycles * num_folds
        split = 1 - (1/float(num_folds))

        self.load()

        for i in range(num_cycles):

            print('\n\nCycle: %d/%d' % ((i+1), num_cycles))

            if Constants.SHUFFLE_DATA:
                self.shuffle()
            self.records = copy.deepcopy(self.original_records)
            self.reviews = copy.deepcopy(self.original_reviews)

            for j in range(num_folds):

                fold_start = time.time()
                cv_start = float(j) / num_folds
                print('\nFold: %d/%d' % ((j+1), num_folds))

                self.create_tmp_file_names()
                self.train_records, self.test_records = \
                    ETLUtils.split_train_test_copy(
                        self.records, split=split, start=cv_start)
                self.train_reviews, self.test_reviews = \
                    ETLUtils.split_train_test_copy(
                        self.reviews, split=split, start=cv_start)
                self.export()
                if Constants.USE_CONTEXT:
                    lda_based_context = self.train_word_model()
                    self.find_reviews_topics(lda_based_context)
                self.prepare()
                self.predict()
                self.evaluate()
                recall = self.top_n_evaluator.recall
                specific_recall = self.top_n_evaluator.specific_recall
                generic_recall = self.top_n_evaluator.generic_recall
                total_recall += recall
                total_specific_recall += specific_recall
                total_generic_recall += generic_recall

                fold_end = time.time()
                fold_time = fold_end - fold_start
                total_cycle_time += fold_time
                self.clear()
                print("Total fold %d time = %f seconds" % ((j+1), fold_time))

        average_recall = total_recall / total_iterations
        average_specific_recall = total_specific_recall / total_iterations
        average_generic_recall = total_generic_recall / total_iterations
        average_cycle_time = total_cycle_time / total_iterations
        print('average recall: %f' % average_recall)
        print('average specific recall: %f' % average_specific_recall)
        print('average generic recall: %f' % average_generic_recall)
        print('average cycle time: %f' % average_cycle_time)
        print('End: %s' % time.strftime("%Y/%m/%d-%H:%M:%S"))

        results = Constants.get_properties_copy()
        results['recall'] = average_recall
        results['specific_recall'] = average_specific_recall
        results['generic_recall'] = average_generic_recall
        results['cycle_time'] = average_cycle_time
        results['timestamp'] = time.strftime("%Y/%m/%d-%H:%M:%S")

        if not os.path.exists(Constants.CSV_RESULTS_FILE):
            with open(Constants.CSV_RESULTS_FILE, 'wb') as f:
                w = csv.DictWriter(f, sorted(results.keys()))
                w.writeheader()
                w.writerow(results)
        else:
            with open(Constants.CSV_RESULTS_FILE, 'a') as f:
                w = csv.DictWriter(f, sorted(results.keys()))
                w.writerow(results)


def run_tests():

    combined_parameters = parameter_combinator.get_combined_parameters()

    test_cycle = 1
    num_tests = len(combined_parameters)
    for properties in combined_parameters:
        Constants.update_properties(properties)
        context_top_n_runner = WordContextTopNRunner()

        print('\n\n******************\nTest %d/%d\n******************\n' %
              (test_cycle, num_tests))

        context_top_n_runner.perform_cross_validation()
        test_cycle += 1


start = time.time()

my_word_context_top_n_runner = WordContextTopNRunner()
my_word_context_top_n_runner.perform_cross_validation()
# run_tests()
# parallel_context_top_n()
end = time.time()
total_time = end - start
print("Total time = %f seconds" % total_time)

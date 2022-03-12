import csv

from datasets import load_dataset
dataset = load_dataset('tweet_eval', 'emotion', split='train')
dataset = dataset.map(lambda x: {'label': dataset.features['label'].names[x['label']]})
part_1 = dataset.filter(lambda x: x['label'] != 'optimism')
part_2 = dataset.filter(lambda x: x['label'] == 'optimism')
part_1.to_pandas().to_csv('tweet_emotion_part1.csv', header=False, index=False, quoting=csv.QUOTE_ALL)
part_2.to_pandas().to_csv('tweet_emotion_part2.csv', header=False, index=False, quoting=csv.QUOTE_ALL)

from fuzzywuzzy import fuzz

def find_best_match(input_text, df):
    input_text = input_text.lower().strip()
    max_score = 0
    best_match = None

    for name in df["Name of medicine"]:
        score = fuzz.token_sort_ratio(input_text, name)
        if score > max_score:
            max_score = score
            best_match = name

    return best_match

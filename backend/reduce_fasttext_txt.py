input_file = "fasttext_training.txt"              # your BIG 11.5 lakh file
output_file = "fasttext_training_reduced.txt"     # new reduced file
MAX_LINES = 50000                                 # reduce to 50k lines

print("Reading large FastText file...")

# ✅ Read all lines
with open(input_file, "r", encoding="utf-8") as f:
    lines = f.readlines()

print("Original line count:", len(lines))

# ✅ Remove duplicates
unique_lines = list(set(lines))
print("After removing duplicates:", len(unique_lines))

# ✅ Sample only 50k (if more than 50k exist)
import random
random.seed(42)

if len(unique_lines) > MAX_LINES:
    unique_lines = random.sample(unique_lines, MAX_LINES)

print("Final reduced line count:", len(unique_lines))
print("Saving reduced file...")

# ✅ Save reduced file
with open(output_file, "w", encoding="utf-8") as f:
    f.writelines(unique_lines)

print("✅ Reduced dataset saved as:", output_file)

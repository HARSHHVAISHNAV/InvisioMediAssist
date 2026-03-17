import spacy
print("spaCy:", spacy.__version__)

print("\nLoading Med7...")
try:
    spacy.load("en_core_med7_lg")
    print("✅ Med7 OK")
except Exception as e:
    print("❌ Med7 ERROR:", e)

print("\nLoading BioNLP...")
try:
    spacy.load("en_ner_bionlp13cg_md")
    print("✅ BioNLP OK")
except Exception as e:
    print("❌ BioNLP ERROR:", e)
import base64
import io
import json
import pandas as pd
import spacy
from flask import Flask, request, jsonify
from PIL import Image
import cv2
import numpy as np
import google.cloud.vision as vision
from fuzzy_matching import find_best_match
import os
from io import BytesIO
from flask_cors import CORS
from fuzzywuzzy import fuzz, process

app = Flask(__name__)
CORS(app)


os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = "D:/InvisioAssist-Backend/invisiomediassist-key.json"



# Load the Medicine Dataset
CSV_PATH = "sorted_cleaned_dataset.csv"
df = pd.read_csv(CSV_PATH)
df["Name of medicine"] = df["Name of medicine"].str.lower().str.strip()

# Load Med7 Model for AI-Based Medicine Recognition
med7 = spacy.load("en_core_med7_lg")

# Initialize Google Cloud Vision Client
client = vision.ImageAnnotatorClient()

# Helper: Extract text using Google Cloud Vision
def extract_text_google_vision(image_bytes):
    image = vision.Image(content=image_bytes)
    response = client.text_detection(image=image)
    texts = response.text_annotations
    return texts[0].description if texts else "No text found"

# Helper: Extract drug name using Med7
def extract_medicine_name_with_med7(text):
    doc = med7(text)
    med_names = [ent.text.lower() for ent in doc.ents if ent.label_ == "DRUG"]
    return med_names[0] if med_names else None

# Helper: Fuzzy match
def find_best_match(input_text, df):
    choices = df["Name of medicine"].dropna().unique()
    best_match, score = process.extractOne(input_text, choices, scorer=fuzz.token_sort_ratio)
    return best_match if score > 60 else None

@app.route("/process_image", methods=["POST"])
def process_image():
    try:
        data = request.get_json()
        image_b64 = data.get("image")

        if not image_b64:
            return jsonify({"error": "Image data not found"}), 400

        # Decode base64 to image
        image_bytes = base64.b64decode(image_b64)
        image = Image.open(io.BytesIO(image_bytes))

        # Convert image to bytes for Google Vision
        img_byte_arr = io.BytesIO()
        image.save(img_byte_arr, format="PNG")
        image_bytes = img_byte_arr.getvalue()

        # OCR using Google Cloud Vision
        extracted_text = extract_text_google_vision(image_bytes)
        print("Extracted Text:", extracted_text)

        # Use Med7 to detect drug name
        detected_medicine = extract_medicine_name_with_med7(extracted_text)
        print("Detected Medicine (Med7):", detected_medicine)

        match_type = ""

        # Case 1: Med7 worked → Try direct match
        if detected_medicine:
            filtered_df = df[df["Name of medicine"] == detected_medicine]
            match_type = "Med7 direct match"

            # Not found? Try partial match (smart fallback)
            if filtered_df.empty:
                print("Med7 result not found in dataset. Trying partial substring match...")
                candidates = df[df["Name of medicine"].str.contains(detected_medicine, case=False, na=False)]
                
                if not candidates.empty:
                    filtered_df = candidates
                    detected_medicine = candidates.iloc[0]["Name of medicine"]
                    match_type = "Partial match using Med7 token"
                else:
                    print("Partial match also failed. Trying fuzzy match...")
                    detected_medicine = find_best_match(detected_medicine, df)
                    filtered_df = df[df["Name of medicine"] == detected_medicine]
                    match_type = "Fuzzy match after Med7 fail"

        # Case 2: Med7 failed → Fuzzy match whole text
        else:
            print("Med7 failed. Trying fuzzy match on full extracted text...")
            detected_medicine = find_best_match(extracted_text, df)
            filtered_df = df[df["Name of medicine"] == detected_medicine]
            match_type = "Fuzzy match (Med7 not detected)"

        # If still not found
        if not detected_medicine or filtered_df.empty:
            return jsonify({"error": "Medicine not recognized"}), 200

        # Found! Build response
        medicine_info = filtered_df.iloc[0]

        response = {
            "medicine_name": detected_medicine,
            "description": medicine_info.get("Full Description", "No description available"),
            "side_effects": medicine_info.get("Side Effects", "No side effects listed"),
            "match_type": match_type
        }

        return jsonify(response), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# OCR using Google Cloud Vision
def extract_text_from_image(image_cv):
    _, encoded_image = cv2.imencode('.jpg', image_cv)
    content = encoded_image.tobytes()

    image = vision.Image(content=content)
    response = client.text_detection(image=image)
    texts = response.text_annotations

    print("Google Cloud Vision Output:", texts)
    if texts:
        return texts[0].description if len(texts) > 0 else "No text found"
    else:
        return "No text detected"

# Med7 Entity Extraction
def extract_medicine_name_with_med7(text):
    doc = med7(text)
    med_names = [ent.text.lower() for ent in doc.ents if ent.label_ == "DRUG"]
    return med_names[0] if med_names else None

if __name__ == '__main__':
    app.run(debug=True)

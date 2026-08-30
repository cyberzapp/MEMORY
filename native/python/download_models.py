import os
import shutil
from huggingface_hub import hf_hub_download

def download_model(repo_id, filename, target_path, token=None):
    print(f"Downloading {filename} from {repo_id}...")
    try:
        # Download the file from HF Hub
        file_path = hf_hub_download(repo_id=repo_id, filename=filename, token=token)
        
        # Ensure target directory exists
        os.makedirs(os.path.dirname(target_path), exist_ok=True)
        
        # Copy the downloaded file to the target location
        shutil.copy(file_path, target_path)
        print(f"Successfully saved to {target_path}")
        return True
    except Exception as e:
        print(f"Error downloading {filename}: {e}")
        return False

if __name__ == "__main__":
    assets_dir = os.path.join("..", "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    
    # 1. Download Embedding Model
    emb_target = os.path.join(assets_dir, "all_minilm_l6_v2.tflite")
    download_model(
        repo_id="Nihal2000/all-MiniLM-L6-v2-quant.tflite",
        filename="all-MiniLM-L6-v2-quant.tflite",
        target_path=emb_target,
        token=os.getenv("HF_TOKEN") # Pass your token via environment variable or replace this string
    )
    
    # Download vocab.txt required for tokenizer
    vocab_target = os.path.join(assets_dir, "vocab.txt")
    download_model(
        repo_id="sentence-transformers/all-MiniLM-L6-v2",
        filename="vocab.txt",
        target_path=vocab_target,
        token=os.getenv("HF_TOKEN")
    )
    
    # 2. Gemma Model (placeholder instruction)
    print(f"NOTE: For Gemma, place 'gemma3-1b-int4.litertlm' manually into {assets_dir} if not downloaded.")

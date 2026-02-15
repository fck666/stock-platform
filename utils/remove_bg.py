from PIL import Image
import os
import sys

# Define input and output paths relative to project root
INPUT_PATH = os.path.join(os.path.dirname(__file__), '..', 'frontend-web', 'public', 'stock-platform.jpg')
OUTPUT_PATH = os.path.join(os.path.dirname(__file__), '..', 'frontend-web', 'public', 'stock-platform-no-bg.png')

def remove_white_bg(input_path, output_path, threshold=230):
    """
    Remove white background from an image.
    :param input_path: Path to the source image (jpg/png)
    :param output_path: Path to save the processed image (png)
    :param threshold: RGB value above which pixels are considered white (0-255)
    """
    try:
        if not os.path.exists(input_path):
            print(f"Error: Input file not found at {input_path}")
            return

        print(f"Processing image: {input_path}")
        img = Image.open(input_path)
        img = img.convert("RGBA")
        datas = img.get_flattened_data() if hasattr(img, 'get_flattened_data') else img.getdata()

        newData = []
        for item in datas:
            # Check if pixel is close to white based on threshold
            # item is (R, G, B, A)
            if item[0] > threshold and item[1] > threshold and item[2] > threshold:
                # Make transparent
                newData.append((255, 255, 255, 0))
            else:
                newData.append(item)

        img.putdata(newData)
        
        # Ensure output directory exists
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        img.save(output_path, "PNG")
        print(f"Successfully saved transparent image to {output_path}")
        
    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    # Allow overriding threshold via command line argument if needed
    threshold = 230
    if len(sys.argv) > 1:
        try:
            threshold = int(sys.argv[1])
        except ValueError:
            pass
            
    remove_white_bg(INPUT_PATH, OUTPUT_PATH, threshold)

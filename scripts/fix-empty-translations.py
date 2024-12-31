import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

def parse_xml_file(file_path):
    """
    Parses a strings.xml or arrays.xml file and returns a dictionary of key-value pairs,
    including string arrays, while ignoring keys with translatable="false".
    """
    tree = ET.parse(file_path)
    root = tree.getroot()
    elements = {}

    # Parse <string> elements
    for string_element in root.findall("string"):
        name = string_element.get("name")
        translatable = string_element.get("translatable")

        if translatable and translatable.lower() == "false":
            continue

        elements[name] = string_element.text

    # Parse <string-array> elements
    for array_element in root.findall("string-array"):
        name = array_element.get("name")
        translatable = array_element.get("translatable")

        if translatable and translatable.lower() == "false":
            continue

        items = [item.text or "" for item in array_element.findall("item")]
        elements[name] = items

    return elements

def write_xml_file(file_path, elements):
    """
    Writes the updated strings and string arrays back to an XML file with proper formatting.
    """
    root = ET.Element("resources")

    for name, value in elements.items():
        if isinstance(value, list):
            # Handle string arrays
            array_element = ET.SubElement(root, "string-array", {"name": name})
            for item in value:
                item_element = ET.SubElement(array_element, "item")
                item_element.text = item
        else:
            # Handle strings
            string_element = ET.SubElement(root, "string", {"name": name})
            string_element.text = value

    rough_string = ET.tostring(root, encoding="utf-8")
    reparsed = minidom.parseString(rough_string)
    pretty_xml = reparsed.toprettyxml(indent="    ")

    with open(file_path, "w", encoding="utf-8") as file:
        file.write(pretty_xml)

def fix_translations(res_dir):
    """
    Fixes translations by filling empty values with the default language's values,
    processing both strings.xml and arrays.xml, while ignoring non-translatable keys.
    """
    default_strings_file = os.path.join(res_dir, "values", "strings.xml")
    default_arrays_file = os.path.join(res_dir, "values", "arrays.xml")

    default_strings = parse_xml_file(default_strings_file) if os.path.exists(default_strings_file) else {}
    default_arrays = parse_xml_file(default_arrays_file) if os.path.exists(default_arrays_file) else {}

    # Iterate through all "values-*" directories
    for dir_name in os.listdir(res_dir):
        if dir_name.startswith("values-"):
            lang_dir = os.path.join(res_dir, dir_name)

            # Process strings.xml
            lang_strings_file = os.path.join(lang_dir, "strings.xml")
            if default_strings:
                lang_strings = parse_xml_file(lang_strings_file) if os.path.exists(lang_strings_file) else {}

                modified = False
                for key, default_value in default_strings.items():
                    if key not in lang_strings or not lang_strings[key]:
                        lang_strings[key] = default_value
                        modified = True

                if modified or not os.path.exists(lang_strings_file):
                    write_xml_file(lang_strings_file, lang_strings)
                    print(f"Updated: {lang_strings_file}")

            # Process arrays.xml
            lang_arrays_file = os.path.join(lang_dir, "arrays.xml")
            if default_arrays:
                lang_arrays = parse_xml_file(lang_arrays_file) if os.path.exists(lang_arrays_file) else {}

                modified = False
                for key, default_value in default_arrays.items():
                    if key not in lang_arrays:
                        lang_arrays[key] = default_value
                        modified = True
                    elif isinstance(default_value, list):
                        # Fill empty items in string arrays
                        for i, item in enumerate(default_value):
                            if i >= len(lang_arrays[key]) or not lang_arrays[key][i]:
                                if len(lang_arrays[key]) <= i:
                                    lang_arrays[key].append(item)
                                else:
                                    lang_arrays[key][i] = item
                                modified = True

                if modified or not os.path.exists(lang_arrays_file):
                    write_xml_file(lang_arrays_file, lang_arrays)
                    print(f"Updated: {lang_arrays_file}")

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Fix empty translations in Android strings.xml and arrays.xml files.")
    parser.add_argument(
        "res_dir",
        type=str,
        nargs="?",
        help="Path to the 'res' directory in your Android project.",
    )

    args = parser.parse_args()

    if not args.res_dir:
        print("Error: No path to the 'res' directory provided.")
        print("Usage: python script.py <path_to_res_directory>")
    elif not os.path.exists(args.res_dir):
        print(f"Error: Directory not found: {args.res_dir}")
    else:
        fix_translations(args.res_dir)
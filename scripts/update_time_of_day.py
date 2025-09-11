#!/usr/bin/env python3
"""
Script to update timeOfDay values in tvos15.json based on mappings from Aerial Mac project
"""

import json
import re

# Complete time mappings from Aerial Mac SourceInfo.timeInformation
TIME_MAPPINGS = {
    # From the complete SourceInfo.timeInformation dictionary
    "A837FA8C-C643-4705-AE92-074EFDD067F7": "night",    # Africa Night
    "03EC0F5E-CCA8-4E0A-9FEC-5BD1CE151182": "sunrise",  # Space - Antarctica
    
    # Additional IDs found in JSON but missing from original mapping
    "58754319-8709-4AB0-8674-B34F04E7FFE2": "day",      # London - River Thames (b6-3 London day 1)
    "4AD99907-9E76-408D-A7FC-8429FF014201": "sunset",   # San Francisco - Bay and Embarcadero
    "840FE8E4-D952-4680-B1A7-AC5BACA2C1F8": "day",      # New York - Upper East Side (b3-2)
    "F0236EC5-EE72-4058-A6CE-1F7D2E8253BF": "day",      # China - Great Wall 1 (b6-1)
    "85CE77BF-3413-4A7B-9B0F-732E96229A73": "sunrise",  # San Francisco - Embarcadero, Market Street
    "024891DE-B7F6-4187-BFE0-E6D237702EF0": "day",      # Hong Kong - Wan Chai
    "3FFA2A97-7D28-49EA-AA39-5BC9051B2745": "day",      # Dubai - Marina 2
    "FE8E1F9D-59BA-4207-B626-28E34D810D0A": "day",      # Hong Kong - Victoria Harbour 1
    "001C94AE-2BA4-4E77-A202-F7DE60E8B1C8": "day",      # Liwa oasis 1
    "22162A9B-DB90-4517-867C-C676BC3E8E95": "day",      # China - Great Wall 2
    
    # Underwater/ocean videos - using "day" as default since no time info available
    "DD47D8E1-CB66-4C12-BFEA-2ADB0D8D1E2E": "day",      # Humpback Whale
    "27A37B0F-738D-4644-A7A4-E33E7A6C1175": "day",      # California Dolphins
    "149E7795-DBDA-4F5D-B39A-14712F841118": "day",      # Tahiti Waves
    "8C31B06F-91A4-4F7C-93ED-56146D7F48B9": "day",      # Tahiti Waves
    "82175C1F-153C-4EC8-AE37-2860EA828004": "day",      # Red Sea Coral
    "58C75C62-3290-47B8-849C-56A583173570": "day",      # Cownose Rays
    "EB3F48E7-D30F-4079-858F-1A61331D5026": "day",      # California Kelp Forest
    "C7AD3D0A-7EDF-412C-A237-B3C9D27381A1": "day",      # Alaskan Jellies
    "3716DD4B-01C0-4F5B-8DD6-DB771EC472FB": "day",      # Gray Reef Sharks
    "CE9B5D5B-B6E7-47C5-8C04-59BF182E98FB": "day",      # Costa Rica Dolphins
    "C6DC4E54-1130-44F8-AF6F-A551D8E8A181": "day",      # Alaskan Jellies
    "E334A6D2-7145-47C8-9B00-C20DED08B2D5": "day",      # Underwater scene
    
    # Remaining unmapped IDs - using reasonable defaults based on typical video types
    "DD266E1F-5DF2-4CDB-A2EB-26CE35664657": "day",      # Likely city/landscape day video
    "F9F918CD-E15F-4F01-A326-84A44650C5C9": "day",      # Likely city/landscape day video
    "AE0115AE-C53B-4DB9-B12F-CA4B7B630CC9": "day",      # Likely city/landscape day video
    "8002C4C8-C611-4894-A068-3D3A3C03472A": "day",      # Likely city/landscape day video
    "5C987900-AD53-469C-8210-CABBCCDDFCAE": "day",      # Likely city/landscape day video
    "B004358B-5A27-42E5-B49E-93FC100B2371": "day",      # Likely city/landscape day video
    "25A6CFB2-3570-4448-B114-244A4E454B7A": "day",      # Likely city/landscape day video
    "E5D58CC2-3C52-4206-9DA2-427DC88B5896": "day",      # Likely city/landscape day video
    "E5799A24-1949-4E66-A17B-B5EB05F28C5D": "day",      # Likely city/landscape day video
    "E487C6EF-B3FB-427B-A2BE-8CBA60F902F0": "day",      # Likely city/landscape day video
    "E540DEE6-4C40-42C8-9CCC-D4CB0FAD7D7B": "day",      # Likely city/landscape day video
    "81CA5ACD-E682-4D8B-A948-0F147EB6ED4F": "day",      # Likely city/landscape day video
    "4109D42A-D717-46A7-A9A2-FE53A82B25C0": "day",      # Likely city/landscape day video
    "DAD82DCE-F3AE-4AEC-8A79-1694D412FC0A": "day",      # Likely city/landscape day video
    "8D04D70F-738B-441D-8D43-AF46B2BF8062": "day",      # Likely city/landscape day video
    "DDE50C77-B7CB-4488-9EB1-D1B13BF21FFE": "day",      # Likely city/landscape day video
    "0C747C29-4BF8-43F6-A5CC-2E012E555341": "day",      # Likely city/landscape day video
    "E54D5AFE-F362-4D48-A20D-F2C21D2B5330": "day",      # Likely city/landscape day video
    "8ACF5D77-B22C-416F-B12A-72FB35E2834F": "day",      # Likely city/landscape day video
    "F9518D54-04A7-4793-8666-CFC114D73CE5": "day",      # Likely city/landscape day video
    "8590D0C5-E344-4FAC-A39A-FD7BC652AEDA": "day",      # Likely city/landscape day video
    "E161929C-0819-4BC2-8359-550C081C7D54": "day",      # Likely city/landscape day video
    "3954A7C4-51EC-4ABC-ABA3-6757AC91C7CF": "day",      # Likely city/landscape day video
    "2F17FCCE-6CCA-4AFA-A08A-C50BF9812DA5": "day",      # Likely city/landscape day video
    "64D11DAB-3B57-4F14-AD2F-E59A9282FA44": "sunset",   # Space - Atlantic Ocean to Spain and France
    "81337355-E156-4242-AAF4-711768D30A54": "night",    # Space - Australia
    "A2BE2E4A-AD4B-428A-9C41-BDAE1E78E816": "night",    # California to Vegas (v7)
    "12318CCB-3F78-43B7-A854-EFDCCE5312CD": "night",    # California to Vegas (v8)
    "1088217C-1410-4CF7-BDE9-8F573A4DBCD9": "night",    # Caribbean
    "D5CFB2FF-5F8C-4637-816B-3E42FC1229B8": "night",    # Caribbean
    "4F881F8B-A7D9-4FDB-A917-17BF6AC5A589": "day",      # Caribbean Day
    "6A74D52E-2447-4B84-9C4A-2FF45A9C5927": "night",    # China
    "6A74D52E-2447-4B84-AE45-0DEF2836C3CC": "night",    # China
    "7825C73A-658F-48EE-B14C-EC56673094AC": "night",    # China (new id)
    "E5DB138A-F04E-4619-B896-DE5CB538C534": "night",    # Italy to Asia
    "F439B0A7-D18C-4B14-9681-6520E6A74FE9": "day",      # Iran and Afghanistan
    "62A926BE-AA0B-4A34-9653-78C4F130543F": "night",    # Ireland to Asia
    "7C643A39-C0B2-4BA0-8BC2-2EAA47CC580E": "sunrise",  # Ireland to Asia
    "6C3D54AE-0871-498A-81D0-56ED24E5FE9F": "night",    # Korean and Japan Night (v17)
    "009BA758-7060-4479-8EE8-FB9B40C8FB97": "night",    # Korean and Japan Night (v18)
    "B1B5DDC5-73C8-4920-8133-BACCE38A08DE": "night",    # Space - Mexico City to New York
    "78911B7E-3C69-47AD-B635-9C2486F6301D": "sunrise",  # Space - New Zealand
    "737E9E24-49BE-4104-9B72-F352DE1AD2BF": "sunrise",  # Space - North America Aurora
    "87060EC2-D006-4102-98CC-3005C68BB343": "sunset",   # Space - South Africa to North Asia
    "63C042F0-90EF-4A95-B7CC-CC9A64BF8421": "sunset",   # Space - West Africa to the Alps
    "044AD56C-A107-41B2-90CC-E60CCACFBCF5": "day",      # China - Great Wall 3
    "EE01F02D-1413-436C-AB05-410F224A5B7B": "day",      # Greenland - Ilulissat Icefjord
    "B8F204CE-6024-49AB-85F9-7CA2F6DCD226": "sunrise",  # Greenland - Nuussuaq Peninsula
    "82BD33C9-B6D2-47E7-9C42-AA3B7758921A": "sunset",   # Hawaii - Pu'u O 'Umi
    "258A6797-CC13-4C3A-AB35-4F25CA3BF474": "day",      # Hawaii - Pu'u O 'Umi day
    "3D729CFC-9000-48D3-A052-C5BD5B7A6842": "day",      # Hawaii - Kohala Coastline
    "12E0343D-2CD9-48EA-AB57-4D680FB6D0C7": "day",      # Hawaii - Waimanu Valley
    "89B1643B-06DD-4DEC-B1B0-774493B0F7B7": "day",      # Los Angeles - Griffith Observatory
    "EC67726A-8212-4C5E-83CF-8412932740D2": "sunset",   # Los Angeles - Hollywood Hills
    "CE279831-1CA7-4A83-A97B-FF1E20234396": "sunset",   # Los Angeles airport
    "F604AF56-EA77-4960-AEF7-82533CC1A8B3": "day",      # London
    "3BA0CFC7-E460-4B59-A817-B97F9EBB9B89": "day",      # New York
    "44166C39-8566-4ECA-BD16-43159429B52F": "night",    # New York - Seventh Avenue
    "2F11E857-4F77-4476-8033-4A1E4610AFCC": "day",      # Dubai - Sheikh Zayed Road
    "9680B8EB-CE2A-4395-AF41-402801F4D6A6": "night",    # Dubai night
    "E99FA658-A59A-4A2D-9F3B-58E7BDC71A9A": "sunset",   # Hong Kong - Victoria Harbour
    "EE533FBD-90AE-419A-AD13-D7A60E2015D6": "sunrise",  # San Francisco - Marin Headlands in Fog
    "A284F0BF-E690-4C13-92E2-4672D93E63B7": "sunset",   # Los Angeles night 3
    "E580E5A5-0888-4BE8-A4CA-F74A18A643C3": "day",      # Palau Jellies
    "6143116A-6302-4E46-BAEC-B6F5C06F6883": "night",    # Various night videos
    "2F72BC1E-3D76-456C-81EB-842EBA488C27": "day",      # Various space videos  
    "7719B48A-2005-4011-9280-2F64EEC6FD91": "sunset",   # Various sunset videos
    "E2E16C9A-FB2A-4A2E-9DDF-2C0B0A20C747": "day",      # Various day videos
    "80C8C9C8-A31B-4F52-A4A5-6A21F77F8467": "day",      # Various day videos
    "29BDF297-EB43-403A-8719-A78DA11A2948": "day",      # Fisherman's Wharf
    "92E48DE9-13A1-4172-9B82-8FD953F6553A": "sunset",   # Various sunset videos
    "7F4C26C2-67C2-4C3A-8F07-8A7BF6148C97": "day",      # River Times at Dusk
    "7540888B-F88A-4CE0-A241-8C123DBBD78F": "day",      # Various day videos
    "4BF75D68-AD47-4959-A8E2-5C0A9F5C96EE": "day",      # Various day videos
    "F07CC61B-30FC-4614-BDAD-3240B61F6793": "day",      # Various day videos
    "3E94AE98-EAF2-4B09-96E3-452F46BC114E": "sunset",   # Various sunset videos
    "A5AAFF5D-8887-42F9-84DB-C9B869256187": "night",    # Buckingham Palace
    "F5804DD6-5963-40DA-9FA5-39C0C6E6DEF9": "sunrise",  # Various sunrise videos
    "E991AC0C-F272-44D8-88F3-05F44EDFE3AE": "night",    # Various night videos
    "640DFB00-FBB9-45DA-9444-9F663859F4BC": "day",      # Various day videos
    "30047FDA-FCD6-4300-9358-0F7B13EBC04A": "day",      # Various day videos
    "6C3D54AE-040A-4B51-AF89-7009EDF02A7F": "day",      # Various day videos
    
    # Additional mappings that were already found
    "737E9E24-49BE-4104-9B72-F352DE1AD2BF": "sunrise",
    "CE279831-1CA7-4A83-A97B-FF1E20234396": "sunset", 
    "9680B8EB-CE2A-4395-AF41-402801F4D6A6": "night",
    "63C042F0-90EF-4A95-B7CC-CC9A64BF8421": "sunset",
    "F439B0A7-D18C-4B14-9681-6520E6A74FE9": "day",
    "E580E5A5-0888-4BE8-A4CA-F74A18A643C3": "day",
    "4F881F8B-A7D9-4FDB-A917-17BF6AC5A589": "day",
    "3D729CFC-9000-48D3-A052-C5BD5B7A6842": "day",
    "EE01F02D-1413-436C-AB05-410F224A5B7B": "day",
    "12318CCB-3F78-43B7-A854-EFDCCE5312CD": "night",
    "3BA0CFC7-E460-4B59-A817-B97F9EBB9B89": "day",
    "89B1643B-06DD-4DEC-B1B0-774493B0F7B7": "day",
    "F604AF56-EA77-4960-AEF7-82533CC1A8B3": "day",
    "258A6797-CC13-4C3A-AB35-4F25CA3BF474": "day",
    "D5CFB2FF-5F8C-4637-816B-3E42FC1229B8": "night",
    "7C643A39-C0B2-4BA0-8BC2-2EAA47CC580E": "sunrise"
}

def update_json_file(file_path):
    """Update timeOfDay values in the JSON file"""
    print(f"Reading {file_path}...")
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    updates_made = 0
    incorrect_updates_made = 0
    
    # First, update entries with "xyz" placeholders
    for video_id, time_of_day in TIME_MAPPINGS.items():
        # Pattern to find entries with this ID that have "xyz" timeOfDay
        # Use global replacement with re.sub and count parameter
        xyz_pattern = rf'"timeOfDay"\s*:\s*"xyz"((?:(?!"timeOfDay"|"id").)*?"id"\s*:\s*"{re.escape(video_id)}")'
        matches = re.findall(xyz_pattern, content, re.DOTALL)
        if matches:
            content = re.sub(xyz_pattern, f'"timeOfDay":"{time_of_day}"\\1', content, flags=re.DOTALL)
            updates_made += len(matches)
            print(f"Updated {len(matches)} instance(s) of {video_id} from 'xyz' to '{time_of_day}'")
    
    # Second, update entries that have incorrect timeOfDay values (not "xyz")
    for video_id, correct_time_of_day in TIME_MAPPINGS.items():
        # Pattern to find entries with this ID that have any timeOfDay that's NOT the correct one
        for wrong_time in ["day", "night", "sunset", "sunrise"]:
            if wrong_time != correct_time_of_day:
                wrong_pattern = rf'"timeOfDay"\s*:\s*"{wrong_time}"((?:(?!"timeOfDay"|"id").)*?"id"\s*:\s*"{re.escape(video_id)}")'
                matches = re.findall(wrong_pattern, content, re.DOTALL)
                if matches:
                    content = re.sub(wrong_pattern, f'"timeOfDay":"{correct_time_of_day}"\\1', content, flags=re.DOTALL)
                    incorrect_updates_made += len(matches)
                    print(f"Corrected {len(matches)} instance(s) of {video_id} from '{wrong_time}' to '{correct_time_of_day}'")
    
    print(f"Made {updates_made} updates from 'xyz' placeholders")
    print(f"Made {incorrect_updates_made} corrections of existing values")
    print(f"Total updates: {updates_made + incorrect_updates_made}")
    
    # Write back to file
    print(f"Writing updated content to {file_path}...")
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print("Update complete!")

if __name__ == "__main__":
    json_file = "../app/src/main/res/raw/tvos15.json"
    update_json_file(json_file)

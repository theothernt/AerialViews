#!/usr/bin/env python3
"""
Add `timeOfDay` and `scene` metadata to `tvos26.json`.

Workflow:
1. Fill in `time_of_day` and `scene` values in `VIDEO_METADATA`.
2. Run `python3 scripts/update_tvos26_metadata.py --check` to validate entries.
3. Run `python3 scripts/update_tvos26_metadata.py --write` to update the JSON file.

Allowed `time_of_day` values:
- day
- night
- sunrise
- sunset

Allowed `scene` values:
- nature
- countryside
- waterfall
- beach
- city
- sea
- space
- patterns
- fire
"""

import argparse
import json
import sys
from pathlib import Path


VALID_TIME_OF_DAY = {"day", "night", "sunrise", "sunset"}
VALID_SCENE = {
    "nature",
    "countryside",
    "waterfall",
    "beach",
    "city",
    "sea",
    "space",
    "patterns",
    "fire",
}

DEFAULT_JSON_PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/tvos26.json"
EXPECTED_ASSET_COUNT = 139


VIDEO_METADATA = [
    {"id": "009BA758-7060-4479-8EE8-FB9B40C8FB97", "title": "Korea and Japan Night", "localized_name_key": "GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT_NAME", "time_of_day": None, "scene": None},
    {"id": "03EC0F5E-CCA8-4E0A-9FEC-5BD1CE151182", "title": "Antarctica", "localized_name_key": "GMT110_112NC_364D_1054_AURORA_ANTARCTICA_NAME", "time_of_day": None, "scene": None},
    {"id": "1088217C-1410-4CF7-BDE9-8F573A4DBCD9", "title": "Caribbean", "localized_name_key": "A105_C002_NAME", "time_of_day": None, "scene": None},
    {"id": "12318CCB-3F78-43B7-A854-EFDCCE5312CD", "title": "California to Vegas", "localized_name_key": "GMT306_139NC_139J_3066_CALI_TO_VEGAS_NAME", "time_of_day": None, "scene": None},
    {"id": "2F72BC1E-3D76-456C-81EB-842EBA488C27", "title": "Africa and the Middle East", "localized_name_key": "A103_C002_0205DG_NAME", "time_of_day": None, "scene": None},
    {"id": "3C4678E4-4D3D-4A40-8817-77752AEA62EB", "title": "Nile Delta", "localized_name_key": "A050_C004_1027V8_NAME", "time_of_day": None, "scene": None},
    {"id": "4F881F8B-A7D9-4FDB-A917-17BF6AC5A589", "title": "Caribbean Day", "localized_name_key": "GMT308_139K_142NC_CARIBBEAN_DAY_NAME", "time_of_day": None, "scene": None},
    {"id": "6324F6EB-E0F1-468F-AC2E-A983EBDDD53B", "title": "China", "localized_name_key": "GMT329_2_113NC_396B_1105_NAME", "time_of_day": None, "scene": None},
    {"id": "63C042F0-90EF-4A95-B7CC-CC9A64BF8421", "title": "West Africa to the Alps", "localized_name_key": "A001_C004_1207W5_NAME", "time_of_day": None, "scene": None},
    {"id": "64D11DAB-3B57-4F14-AD2F-E59A9282FA44", "title": "Atlantic Ocean to Spain and France", "localized_name_key": "A001_C001_120530_NAME", "time_of_day": None, "scene": None},
    {"id": "737E9E24-49BE-4104-9B72-F352DE1AD2BF", "title": "North America Aurora", "localized_name_key": "GMT314_139M_170NC_NORTH_AMERICA_AURORA_NAME", "time_of_day": None, "scene": None},
    {"id": "7719B48A-2005-4011-9280-2F64EEC6FD91", "title": "Southern California to Baja", "localized_name_key": "A114_C001_NAME", "time_of_day": None, "scene": None},
    {"id": "78911B7E-3C69-47AD-B635-9C2486F6301D", "title": "New Zealand", "localized_name_key": "A105_C003_0212CT_NAME", "time_of_day": None, "scene": None},
    {"id": "7C643A39-C0B2-4BA0-8BC2-2EAA47CC580E", "title": "Ireland to Asia", "localized_name_key": "GMT329_117NC_401C_1037_IRELAND_TO_ASIA_NAME", "time_of_day": None, "scene": None},
    {"id": "81337355-E156-4242-AAF4-711768D30A54", "title": "Australia", "localized_name_key": "GMT060_117NC_363D_1034_AUSTRALIA_NAME", "time_of_day": None, "scene": None},
    {"id": "87060EC2-D006-4102-98CC-3005C68BB343", "title": "South Africa to North Asia", "localized_name_key": "A351_C001_1213SK_NAME", "time_of_day": None, "scene": None},
    {"id": "A837FA8C-C643-4705-AE92-074EFDD067F7", "title": "Africa Night", "localized_name_key": "GMT312_162NC_139M_1041_AFRICA_NIGHT_NAME", "time_of_day": None, "scene": None},
    {"id": "B1B5DDC5-73C8-4920-8133-BACCE38A08DE", "title": "New York Night", "localized_name_key": "GMT307_136NC_134K_8277_NY_NIGHT_NAME", "time_of_day": None, "scene": None},
    {"id": "D5CFB2FF-5F8C-4637-816B-3E42FC1229B8", "title": "Caribbean", "localized_name_key": "A108_C001_NAME", "time_of_day": None, "scene": None},
    {"id": "E556BBC5-D0A0-4DB1-AC77-BC76E4A526F4", "title": "Sahara and Italy", "localized_name_key": "A009_C001_10181A_NAME", "time_of_day": None, "scene": None},
    {"id": "E5DB138A-F04E-4619-B896-DE5CB538C534", "title": "Italy to Asia", "localized_name_key": "GMT329_113NC_396B_1105_ITALY_TO_ASIA_NAME", "time_of_day": None, "scene": None},
    {"id": "F439B0A7-D18C-4B14-9681-6520E6A74FE9", "title": "Iran and Afghanistan", "localized_name_key": "A083_C002_1130KZ_NAME", "time_of_day": None, "scene": None},
    {"id": "001C94AE-2BA4-4E77-A202-F7DE60E8B1C8", "title": "Liwa", "localized_name_key": "LW_L001_C006_NAME", "time_of_day": None, "scene": None},
    {"id": "044AD56C-A107-41B2-90CC-E60CCACFBCF5", "title": "China", "localized_name_key": "C003_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "0C747C29-4BF8-43F6-A5CC-2E012E555341", "title": "Scotland", "localized_name_key": "S003_C020_NAME", "time_of_day": None, "scene": None},
    {"id": "12E0343D-2CD9-48EA-AB57-4D680FB6D0C7", "title": "Hawaii", "localized_name_key": "H007_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "22162A9B-DB90-4517-867C-C676BC3E8E95", "title": "China", "localized_name_key": "C004_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "258A6797-CC13-4C3A-AB35-4F25CA3BF474", "title": "Hawaii", "localized_name_key": "H004_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "25A6CFB2-3570-4448-B114-244A4E454B7A", "title": "Patagonia", "localized_name_key": "P006_C002_11106T_NAME", "time_of_day": None, "scene": None},
    {"id": "2F17FCCE-6CCA-4AFA-A08A-C50BF9812DA5", "title": "Iceland", "localized_name_key": "I005_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "2F52E34C-39D4-4AB1-9025-8F7141FAA720", "title": "Greenland", "localized_name_key": "GL_G002_C002_NAME", "time_of_day": None, "scene": None},
    {"id": "3954A7C4-51EC-4ABC-ABA3-6757AC91C7CF", "title": "Scotland", "localized_name_key": "S005_C015_NAME", "time_of_day": None, "scene": None},
    {"id": "3D729CFC-9000-48D3-A052-C5BD5B7A6842", "title": "Hawaii", "localized_name_key": "H012_C009_0_NAME", "time_of_day": None, "scene": None},
    {"id": "4109D42A-D717-46A7-A9A2-FE53A82B25C0", "title": "Yosemite", "localized_name_key": "Y011_C001_0305_NAME", "time_of_day": None, "scene": None},
    {"id": "499995FA-E51A-4ACE-8DFD-BDF8AFF6C943", "title": "Hawaii", "localized_name_key": "H005_C012_NAME", "time_of_day": None, "scene": None},
    {"id": "5C987900-AD53-469C-8210-CABBCCDDFCAE", "title": "Patagonia", "localized_name_key": "P001_C005_11059D_NAME", "time_of_day": None, "scene": None},
    {"id": "8002C4C8-C611-4894-A068-3D3A3C03472A", "title": "Grand Canyon", "localized_name_key": "G010_C026_0107KE_NAME", "time_of_day": None, "scene": None},
    {"id": "81CA5ACD-E682-4D8B-A948-0F147EB6ED4F", "title": "Yosemite", "localized_name_key": "Y003_C009_027_NAME", "time_of_day": None, "scene": None},
    {"id": "82BD33C9-B6D2-47E7-9C42-AA3B7758921A", "title": "Hawaii", "localized_name_key": "H004_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "8590D0C5-E344-4FAC-A39A-FD7BC652AEDA", "title": "Iceland", "localized_name_key": "I003_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "8ACF5D77-B22C-416F-B12A-72FB35E2834F", "title": "Iceland", "localized_name_key": "I004_C014_NAME", "time_of_day": None, "scene": None},
    {"id": "8D04D70F-738B-441D-8D43-AF46B2BF8062", "title": "Yosemite", "localized_name_key": "Y011_C008_030584_NAME", "time_of_day": None, "scene": None},
    {"id": "9CCB8297-E9F5-4699-AE1F-890CFBD5E29C", "title": "China", "localized_name_key": "CH_C002_C005_NAME", "time_of_day": None, "scene": None},
    {"id": "AE0115AE-C53B-4DB9-B12F-CA4B7B630CC9", "title": "Grand Canyon", "localized_name_key": "G009_C014_0106B9_NAME", "time_of_day": None, "scene": None},
    {"id": "AFA22C08-A486-4CE8-9A13-E355B6C38559", "title": "Liwa", "localized_name_key": "LW_L001_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "B004358B-5A27-42E5-B49E-93FC100B2371", "title": "Patagonia", "localized_name_key": "P005_C002_1109E1_NAME", "time_of_day": None, "scene": None},
    {"id": "B876B645-3955-420E-99DF-60139E451CF3", "title": "China", "localized_name_key": "CH_C007_C011_NAME", "time_of_day": None, "scene": None},
    {"id": "B8F204CE-6024-49AB-85F9-7CA2F6DCD226", "title": "Greenland", "localized_name_key": "GL_G004_C010_NAME", "time_of_day": None, "scene": None},
    {"id": "D5E76230-81A3-4F65-A1BA-51B8CADED625", "title": "China", "localized_name_key": "CH_C007_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "DAD82DCE-F3AE-4AEC-8A79-1694D412FC0A", "title": "Yosemite", "localized_name_key": "Y009_C015_0304I_NAME", "time_of_day": None, "scene": None},
    {"id": "DD266E1F-5DF2-4CDB-A2EB-26CE35664657", "title": "Grand Canyon", "localized_name_key": "G008_C015_0106MB_NAME", "time_of_day": None, "scene": None},
    {"id": "DDE50C77-B7CB-4488-9EB1-D1B13BF21FFE", "title": "Iceland", "localized_name_key": "I003_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "E161929C-0819-4BC2-8359-550C081C7D54", "title": "Scotland", "localized_name_key": "S006_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "E334A6D2-7145-47C8-9B00-C20DED08B2D5", "title": "Grand Canyon", "localized_name_key": "G007_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "E487C6EF-B3FB-427B-A2BE-8CBA60F902F0", "title": "Yosemite", "localized_name_key": "Y005_C003_0228SC_NAME", "time_of_day": None, "scene": None},
    {"id": "E540DEE6-4C40-42C8-9CCC-D4CB0FAD7D7B", "title": "Yosemite", "localized_name_key": "Y002_C013_0226_NAME", "time_of_day": None, "scene": None},
    {"id": "E54D5AFE-F362-4D48-A20D-F2C21D2B5330", "title": "Iceland", "localized_name_key": "I003_C011_NAME", "time_of_day": None, "scene": None},
    {"id": "E5799A24-1949-4E66-A17B-B5EB05F28C5D", "title": "Yosemite", "localized_name_key": "Y004_C015_0227PD_NAME", "time_of_day": None, "scene": None},
    {"id": "E5D58CC2-3C52-4206-9DA2-427DC88B5896", "title": "Patagonia", "localized_name_key": "P007_C027_NAME", "time_of_day": None, "scene": None},
    {"id": "EE01F02D-1413-436C-AB05-410F224A5B7B", "title": "Greenland", "localized_name_key": "GL_G010_C006_NAME", "time_of_day": None, "scene": None},
    {"id": "F0236EC5-EE72-4058-A6CE-1F7D2E8253BF", "title": "China", "localized_name_key": "C001_C005_NAME", "time_of_day": None, "scene": None},
    {"id": "F9518D54-04A7-4793-8666-CFC114D73CE5", "title": "Iceland", "localized_name_key": "I003_C015_NAME", "time_of_day": None, "scene": None},
    {"id": "F9F918CD-E15F-4F01-A326-84A44650C5C9", "title": "Grand Canyon", "localized_name_key": "G009_C003_010678_NAME", "time_of_day": None, "scene": None},
    {"id": "00BA71CD-2C54-415A-A68A-8358E677D750", "title": "Dubai", "localized_name_key": "DB_D002_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "024891DE-B7F6-4187-BFE0-E6D237702EF0", "title": "Hong Kong", "localized_name_key": "HK_H004_C013_NAME", "time_of_day": None, "scene": None},
    {"id": "29BDF297-EB43-403A-8719-A78DA11A2948", "title": "San Francisco", "localized_name_key": "A007_C017_NAME", "time_of_day": None, "scene": None},
    {"id": "35693AEA-F8C4-4A80-B77D-C94B20A68956", "title": "Los Angeles", "localized_name_key": "LA_A005_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "3BA0CFC7-E460-4B59-A817-B97F9EBB9B89", "title": "New York", "localized_name_key": "N008_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "3E94AE98-EAF2-4B09-96E3-452F46BC114E", "title": "San Francisco", "localized_name_key": "A015_C018_NAME", "time_of_day": None, "scene": None},
    {"id": "3FFA2A97-7D28-49EA-AA39-5BC9051B2745", "title": "Dubai", "localized_name_key": "DB_D001_C005_NAME", "time_of_day": None, "scene": None},
    {"id": "44166C39-8566-4ECA-BD16-43159429B52F", "title": "New York", "localized_name_key": "N013_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "4AD99907-9E76-408D-A7FC-8429FF014201", "title": "San Francisco", "localized_name_key": "A013_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "58754319-8709-4AB0-8674-B34F04E7FFE2", "title": "London", "localized_name_key": "L010_C006_NAME", "time_of_day": None, "scene": None},
    {"id": "640DFB00-FBB9-45DA-9444-9F663859F4BC", "title": "New York", "localized_name_key": "N008_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "72B4390D-DF1D-4D51-B179-229BBAEFFF2C", "title": "San Francisco", "localized_name_key": "A013_C012_NAME", "time_of_day": None, "scene": None},
    {"id": "7F4C26C2-67C2-4C3A-8F07-8A7BF6148C97", "title": "London", "localized_name_key": "L004_C011_NAME", "time_of_day": None, "scene": None},
    {"id": "840FE8E4-D952-4680-B1A7-AC5BACA2C1F8", "title": "New York", "localized_name_key": "N003_C006_NAME", "time_of_day": None, "scene": None},
    {"id": "85CE77BF-3413-4A7B-9B0F-732E96229A73", "title": "San Francisco", "localized_name_key": "A012_C014_NAME", "time_of_day": None, "scene": None},
    {"id": "876D51F4-3D78-4221-8AD2-F9E78C0FD9B9", "title": "Dubai", "localized_name_key": "DB_D008_C010_NAME", "time_of_day": None, "scene": None},
    {"id": "89B1643B-06DD-4DEC-B1B0-774493B0F7B7", "title": "Los Angeles", "localized_name_key": "LA_A009_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "92E48DE9-13A1-4172-B560-29B4668A87EE", "title": "Los Angeles", "localized_name_key": "LA_A008_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "9680B8EB-CE2A-4395-AF41-402801F4D6A6", "title": "Dubai", "localized_name_key": "DB_D011_C010_NAME", "time_of_day": None, "scene": None},
    {"id": "A5AAFF5D-8887-42BB-8AFD-867EF557ED85", "title": "London", "localized_name_key": "L007_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "C8559883-6F3E-4AF2-8960-903710CD47B7", "title": "Hong Kong", "localized_name_key": "HK_H004_C010_NAME", "time_of_day": None, "scene": None},
    {"id": "CE279831-1CA7-4A83-A97B-FF1E20234396", "title": "Los Angeles", "localized_name_key": "LA_A006_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "DE851E6D-C2BE-4D9F-AB54-0F9CE994DC51", "title": "San Francisco", "localized_name_key": "A006_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "E991AC0C-F272-44D8-88F3-05F44EDFE3AE", "title": "Dubai", "localized_name_key": "DB_D001_C001_NAME", "time_of_day": None, "scene": None},
    {"id": "E99FA658-A59A-4A2D-9F3B-58E7BDC71A9A", "title": "Hong Kong", "localized_name_key": "HK_B005_C011_NAME", "time_of_day": None, "scene": None},
    {"id": "EC67726A-8212-4C5E-83CF-8412932740D2", "title": "Los Angeles", "localized_name_key": "LA_A006_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "EE533FBD-90AE-419A-AD13-D7A60E2015D6", "title": "San Francisco", "localized_name_key": "A008_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "F5804DD6-5963-40DA-9FA0-39C0C6E6DEF9", "title": "Los Angeles", "localized_name_key": "LA_A011_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "F604AF56-EA77-4960-AEF7-82533CC1A8B3", "title": "London", "localized_name_key": "L012_C002_NAME", "time_of_day": None, "scene": None},
    {"id": "FE8E1F9D-59BA-4207-B626-28E34D810D0A", "title": "Hong Kong", "localized_name_key": "HK_H004_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "149E7795-DBDA-4F5D-B39A-14712F841118", "title": "Tahiti Waves", "localized_name_key": "TH_803_A001_8_NAME", "time_of_day": None, "scene": None},
    {"id": "27A37B0F-738D-4644-A7A4-E33E7A6C1175", "title": "California Dolphins", "localized_name_key": "B002_C011_NAME", "time_of_day": None, "scene": None},
    {"id": "2B30E324-E4FF-4CC1-BA45-A958C2D2B2EC", "title": "Barracuda", "localized_name_key": "BO_A018_C029_NAME", "time_of_day": None, "scene": None},
    {"id": "3716DD4B-01C0-4F5B-8DD6-DB771EC472FB", "title": "Gray Reef Sharks", "localized_name_key": "U009_C004_NAME", "time_of_day": None, "scene": None},
    {"id": "537A4DAB-83B0-4B66-BCD1-05E5DBB4A268", "title": "Jacks", "localized_name_key": "A014_C023_NAME", "time_of_day": None, "scene": None},
    {"id": "581A4F1A-2B6D-468C-A1BE-6F473F06D10B", "title": "Sea Stars", "localized_name_key": "A012_C031_NAME", "time_of_day": None, "scene": None},
    {"id": "58C75C62-3290-47B8-849C-56A583173570", "title": "Cownose Rays", "localized_name_key": "A006_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "6143116D-03BB-485E-864E-A8CF58ACF6F1", "title": "Kelp", "localized_name_key": "KP_A010_C002_NAME", "time_of_day": None, "scene": None},
    {"id": "687D03A2-18A5-4181-8E85-38F3A13409B9", "title": "Bumpheads", "localized_name_key": "BO_A014_C008_NAME", "time_of_day": None, "scene": None},
    {"id": "82175C1F-153C-4EC8-AE37-2860EA828004", "title": "Red Sea Coral", "localized_name_key": "A008_C010_NAME", "time_of_day": None, "scene": None},
    {"id": "83C65C90-270C-4490-9C69-F51FE03D7F06", "title": "Seals", "localized_name_key": "SE_A016_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "8C31B06F-91A4-4F7C-93ED-56146D7F48B9", "title": "Tahiti Waves", "localized_name_key": "TH_804_A001_8_NAME", "time_of_day": None, "scene": None},
    {"id": "BA4ECA11-592F-4727-9221-D2A32A16EB28", "title": "Palau Jellies", "localized_name_key": "PA_A001_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "C6DC4E54-1130-44F8-AF6F-A551D8E8A181", "title": "Alaskan Jellies", "localized_name_key": "A004_C012_NAME", "time_of_day": None, "scene": None},
    {"id": "C7AD3D0A-7EDF-412C-A237-B3C9D27381A1", "title": "Alaskan Jellies", "localized_name_key": "A003_C014_NAME", "time_of_day": None, "scene": None},
    {"id": "CE9B5D5B-B6E7-47C5-8C04-59BF182E98FB", "title": "Costa Rica Dolphins", "localized_name_key": "A009_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "DD47D8E1-CB66-4C12-BFEA-2ADB0D8D1E2E", "title": "Humpback Whale", "localized_name_key": "D004_L014_NAME", "time_of_day": None, "scene": None},
    {"id": "E580E5A5-0888-4BE8-A4CA-F74A18A643C3", "title": "Palau Jellies", "localized_name_key": "PA_A002_C009_NAME", "time_of_day": None, "scene": None},
    {"id": "EB3F48E7-D30F-4079-858F-1A61331D5026", "title": "California Kelp Forest", "localized_name_key": "A016_C002_NAME", "time_of_day": None, "scene": None},
    {"id": "EC3DC957-D4C2-4732-AACE-7D0C0F390EC8", "title": "Palau Jellies", "localized_name_key": "PA_A010_C007_NAME", "time_of_day": None, "scene": None},
    {"id": "F07CC61B-30FC-4614-BDAD-3240B61F6793", "title": "Palau Coral", "localized_name_key": "PA_A004_C003_NAME", "time_of_day": None, "scene": None},
    {"id": "F390FE3B-FA61-483D-BADC-2447F89951BA", "title": "California Wildflowers", "localized_name_key": "W014_C018_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "4A3590EC-FF30-41E7-85FE-210FF6112917", "title": "California State Route 58, Carrizo Plain, California", "localized_name_key": "W015_C010_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "473C2FDC-0B75-497A-B1FE-AA1863C9C885", "title": "California Wildflowers", "localized_name_key": "W015_C006_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "AA5E82B9-289A-480C-A14B-242989107275", "title": "Del Norte Coast Redwoods State Park, California", "localized_name_key": "R010_C003_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "97447D85-960C-4B2A-A101-048284D95853", "title": "Redwoods", "localized_name_key": "R013_C039_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "8A57476A-E177-4AAD-B317-643F681584E1", "title": "China Beach, Curry County, Oregon", "localized_name_key": "R006_C013_S05_NAME", "time_of_day": None, "scene": None},
    {"id": "15A8BC97-45AC-45DC-9AF9-313808C578BC", "title": "Secret Beach, Brookings, Oregon", "localized_name_key": "R004_C012_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "AB7FC3C3-8853-45CD-AB6E-89F0985C2922", "title": "Monument Valley, Utah", "localized_name_key": "M012_C065_S04_NAME", "time_of_day": None, "scene": None},
    {"id": "47BC0599-72E7-43C4-8BE1-CBCE2432E2A5", "title": "Coal Mine Canyon, Arizona", "localized_name_key": "M013_C012_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "7530C83C-8F7B-42C6-BB71-5FA2ED070BEC", "title": "Cathedral Canyon, Lake Powell, Utah", "localized_name_key": "M010_C005_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "100858D2-FE01-4B70-8E2D-3FCF20AFE6B5", "title": "Monument Valley, Utah", "localized_name_key": "M007_C007_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "1A17ED86-9E0D-4DF2-8CF3-5AB5DB67A348", "title": "Factory Butte, Wayne County, Utah", "localized_name_key": "M012_C023_S04_NAME", "time_of_day": None, "scene": None},
    {"id": "D759828B-4BAB-456B-AD75-225BA238F925", "title": "Twilight, Forbidding, and Cascade Canyon, Lake Powell, Utah", "localized_name_key": "M010_C009_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "A168628E-11EE-4456-AD66-E7E3E47D1B21", "title": "Olympia Bar, Lake Powell, Utah", "localized_name_key": "M005_C017_F01_NAME", "time_of_day": None, "scene": None},
    {"id": "5929F26F-992D-4729-AFCC-BCDF23D15C32", "title": "Ghansali from Above", "localized_name_key": "ANN0010_NAME", "time_of_day": None, "scene": None},
    {"id": "73120738-3D8D-47E7-B6E9-8AAC4D352577", "title": "Through Himalayas", "localized_name_key": "ANN0020_NAME", "time_of_day": None, "scene": None},
    {"id": "9C77AC8C-B23D-4ED9-ABD5-73D3CAD38317", "title": "Tea Gardens from Above", "localized_name_key": "ANN0040_NAME", "time_of_day": None, "scene": None},
    {"id": "B13D40FC-C033-436D-A197-185900EC3552", "title": "Goa Beaches", "localized_name_key": "ANN0060_NAME", "time_of_day": None, "scene": None},
    {"id": "20633C28-FF90-4C54-B828-BB7F1669F7B4", "title": "Reservoir Day", "localized_name_key": "ANN0070_NAME", "time_of_day": None, "scene": None},
    {"id": "8A51AAE4-6ED6-432B-A222-4081D1F29D24", "title": "Goa Coast", "localized_name_key": "ANN0080_NAME", "time_of_day": None, "scene": None},
    {"id": "0FA98CA1-0005-4C98-8DCF-8BE7A6B159CE", "title": "Himalayas Day", "localized_name_key": "ANN0090_NAME", "time_of_day": None, "scene": None},
    {"id": "4E9FE515-65B1-4677-B1A0-CAB75BDA1480", "title": "Tea Gardens Day", "localized_name_key": "ANN0100_NAME", "time_of_day": None, "scene": None},
    {"id": "B6461ECC-44F5-4BC9-877F-484A605D0D10", "title": "The Ganges", "localized_name_key": "ANN0110_NAME", "time_of_day": None, "scene": None},
    {"id": "A63179A7-BC32-4307-89F0-112694889A03", "title": "Himalayan Peaks", "localized_name_key": "ANN0120_NAME", "time_of_day": None, "scene": None},
    {"id": "A92E4A3F-9BFC-4772-B3A7-025B54AB1D3D", "title": "Tea Gardens Mist", "localized_name_key": "ANN0130_NAME", "time_of_day": None, "scene": None},
]


def normalize_value(value):
    if value is None:
        return None
    if isinstance(value, str):
        stripped = value.strip().lower()
        return stripped or None
    return value


def validate_metadata(entries):
    seen_ids = set()
    errors = []
    missing = []

    for entry in entries:
        video_id = entry["id"]
        if video_id in seen_ids:
            errors.append("Duplicate video id in VIDEO_METADATA: %s" % video_id)
        seen_ids.add(video_id)

        time_of_day = normalize_value(entry.get("time_of_day"))
        scene = normalize_value(entry.get("scene"))
        entry["time_of_day"] = time_of_day
        entry["scene"] = scene

        if time_of_day is not None and time_of_day not in VALID_TIME_OF_DAY:
            errors.append(
                "Invalid time_of_day for %s (%s): %r"
                % (video_id, entry["title"], time_of_day)
            )

        if scene is not None and scene not in VALID_SCENE:
            errors.append(
                "Invalid scene for %s (%s): %r"
                % (video_id, entry["title"], scene)
            )

        if time_of_day is None or scene is None:
            missing.append(entry)

    return errors, missing


def insert_metadata_fields(asset, time_of_day, scene):
    result = {}
    inserted = False

    for key, value in asset.items():
        result[key] = value
        if key == "pointsOfInterest":
            result["timeOfDay"] = time_of_day
            result["scene"] = scene
            inserted = True

    if not inserted:
        result["timeOfDay"] = time_of_day
        result["scene"] = scene

    return result


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path, payload):
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=4, ensure_ascii=False)
        handle.write("\n")


def print_missing(entries):
    if not entries:
        print("All VIDEO_METADATA entries have time_of_day and scene values.")
        return

    print("Entries still missing metadata: %d" % len(entries))
    for entry in entries:
        status = []
        if entry["time_of_day"] is None:
            status.append("time_of_day")
        if entry["scene"] is None:
            status.append("scene")
        print(
            "- %s | %s | missing %s"
            % (entry["id"], entry["title"], ", ".join(status))
        )


def apply_metadata(json_path, entries, require_complete):
    errors, missing = validate_metadata(entries)
    if errors:
        for error in errors:
            print("ERROR:", error, file=sys.stderr)
        return 1

    if require_complete and missing:
        print_missing(missing)
        print("Refusing to write because metadata is incomplete.", file=sys.stderr)
        return 1

    payload = load_json(json_path)
    assets = payload.get("assets", [])

    if len(assets) != EXPECTED_ASSET_COUNT:
        print(
            "WARNING: expected %d assets in %s but found %d"
            % (EXPECTED_ASSET_COUNT, json_path, len(assets)),
            file=sys.stderr,
        )

    by_id = {entry["id"]: entry for entry in entries}
    asset_ids = {asset.get("id") for asset in assets}
    metadata_ids = set(by_id)

    missing_from_script = sorted(asset_ids - metadata_ids)
    missing_from_json = sorted(metadata_ids - asset_ids)

    if missing_from_script:
        print("ERROR: JSON contains ids missing from VIDEO_METADATA:", file=sys.stderr)
        for video_id in missing_from_script:
            print(" - %s" % video_id, file=sys.stderr)
        return 1

    if missing_from_json:
        print("ERROR: VIDEO_METADATA contains ids missing from JSON:", file=sys.stderr)
        for video_id in missing_from_json:
            print(" - %s" % video_id, file=sys.stderr)
        return 1

    updated_assets = []
    updated_count = 0
    skipped_count = 0

    for asset in assets:
        entry = by_id[asset["id"]]
        time_of_day = entry["time_of_day"]
        scene = entry["scene"]

        if time_of_day is None or scene is None:
            updated_assets.append(asset)
            skipped_count += 1
            continue

        updated_assets.append(insert_metadata_fields(asset, time_of_day, scene))
        updated_count += 1

    payload["assets"] = updated_assets
    write_json(json_path, payload)

    print("Updated %d assets in %s" % (updated_count, json_path))
    if skipped_count:
        print("Skipped %d assets that still have incomplete metadata." % skipped_count)

    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Validate and apply timeOfDay/scene metadata to tvos26.json."
    )
    parser.add_argument(
        "--json",
        default=str(DEFAULT_JSON_PATH),
        help="Path to the tvos26 JSON file.",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Write metadata into the JSON file.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate metadata and print missing entries without writing.",
    )
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help="Fail if any VIDEO_METADATA entries still have missing values.",
    )
    args = parser.parse_args()

    errors, missing = validate_metadata(VIDEO_METADATA)
    if errors:
        for error in errors:
            print("ERROR:", error, file=sys.stderr)
        return 1

    json_path = Path(args.json)
    if not json_path.exists():
        print("ERROR: JSON file not found: %s" % json_path, file=sys.stderr)
        return 1

    if args.check or not args.write:
        print("VIDEO_METADATA entries: %d" % len(VIDEO_METADATA))
        print_missing(missing)
        if args.require_complete and missing:
            return 1
        if not args.write:
            return 0

    return apply_metadata(
        json_path=json_path,
        entries=VIDEO_METADATA,
        require_complete=args.require_complete,
    )


if __name__ == "__main__":
    raise SystemExit(main())

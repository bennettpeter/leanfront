#!/usr/bin/python3
# -*- coding: utf-8 -*-
#
# Python script to check, add, remove, amend strings in the translation JSON files
# used by the Angular MythTV webapp
#
# run ./translation-util.py to display help info
#
# Required modules: googletrans and flatten_dict
#
# run sudo pip3 install googletrans==3.1.0a0 to install google trans.
# On ubuntu 24.04 pip3 is disabled, so do the following in your home directory
# This creates directory "python" in your home directory
# python3 -m venv python
# source python/bin/activate
# pip3 install googletrans==3.1.0a0
#
# Then before running ./translation-util.py, run this:
# export PYTHONPATH=/home/peter/python/lib/python3.12/site-packages

import os, sys, re
import json
import xml.etree.ElementTree as ET
# TODO - replace optparse with argparse
from optparse import OptionParser
from googletrans import Translator
from httpx import Timeout

DEFAULT_LANG = ('default', 'values', 'Default')

DEST_LANGS = [
            ('af',    'values-af',         'Afrikaans'),
            ('cs',    'values-cs',         'Czech'),
            ('da',    'values-da',         'Danish'),
            ('de',    'values-de',         'German'),
            ('el',    'values-el',         'Greek'),
            ('es',    'values-es',         'Spanish'),
            ('fi',    'values-fi',         'Finnish'),
            ('fr',    'values-fr',         'French'),
            ('it',    'values-it',         'Italian'),
            ('nl',    'values-nl',         'Dutch'),
            ('no',    'values-nb',         'Norwegian'),
            ('pl',    'values-pl',         'Polish'),
            ('pt',    'values-pt',         'Portuguese'),
            ('sv',    'values-sv',         'Swedish')
        ]

def doCheckTranslations(default_dict, code, location, desc, keylist:list):
    # load dest language file
    dest_dict = loadFile(location, desc)
    for key, xvalue in default_dict.items():
        destString = dest_dict.get(key)
        if destString == None or destString == "" or key in keylist:
            value = xvalue.replace("\\'","'")
            destString = translate(value, code)
            xdestString = destString.replace("'","\\'")
            print("Updating string '%s' -> '%s'" % (value, destString))
            dest_dict[key] = xdestString
    saveFile(location, default_dict, dest_dict)

def checkTranslations(keylist:list):
    default_dict = loadDefault()
    # Check specific keys to force translate on
    error = False
    for key in keylist:
        if key not in default_dict:
            print ("Error invalid key: " + key)
            error = True
    if error:
        sys.exit(2)

    # loop through all the language files
    for dest_code, dest_location, dest_desc in DEST_LANGS:
        doCheckTranslations(default_dict, dest_code, dest_location, dest_desc, keylist)

    print("\nAll languages checked OK")

def translate(src_text, lang):
    # just pass through the UK and Canadian English strings
    if lang == 'en_US' or lang == 'en_UK' or lang == 'en_CA':
        return src_text

    # just use Spanish for now
    if lang == 'es_ES':
        lang = 'es'

    translation = translator.translate(src_text, dest=lang, src='en')
    result = translation.text

    return result

#   load the default strings into a dict
def loadDefault():
    return loadFile(DEFAULT_LANG[1], DEFAULT_LANG[2])

def loadFile(src_location, src_desc):
    # open source file
    print("Opening source language %s from %s" % (src_desc, src_location))
    src_filename = translation_dir + src_location + "/strings.xml"
    src_dict = dict()
    if os.path.isfile(src_filename):
        tree = ET.parse(src_filename)
        root = tree.getroot()
        for el in root:
            if el.tag == "string":
                if el.attrib.get("translatable") != "false":
                    src_dict[el.attrib["name"]] = el.text
    return src_dict

# Only save in the dest file keys which also exist in the default file.
def saveFile(location: str, src_dict: dict, dest_dict: dict):
    root = ET.Element("resources")
    tree = ET.ElementTree(root)
    dest_copy = dest_dict.copy()
    for key,value in src_dict.items():
        el = ET.Element("string",dict({("name",key)}))
        el.text = dest_dict.get(key,value)
        root.append(el)
        del dest_copy[key]
    if len(dest_copy) > 0:
        print ("WARNING: Strings deleted from " + location + ":")
        print (dest_copy)
    print("Saving file for " + location)
    dest_filename = translation_dir + location + "/strings.xml"
    ET.indent(tree)
    tree.write(dest_filename, encoding="utf-8", xml_declaration=True)

def listKeys(lang):
    # find language
    found = False

    code, location, desc = DEFAULT_LANG

    if lang == code:
        found = True

    if not found:
        for code, location, desc in DEST_LANGS:
            if lang == code:
                found = True
                break
    if not found:
        print("ERROR: language code not recognized '%s' - Aborting!" % lang)
        sys.exit(1)

    print("Showing keys and string for language %s from %s" % (desc, location))

    d = loadFile(location, desc)

    for key, value in d.items():
        print("{:<50} {:<100}".format(key, str(value)))

def listLanguages():
    for code,file,name in DEST_LANGS:
        print("{0:6}  {1:12}  {2}".format(code,file,name))

if __name__ == '__main__':

    global translator
    translator = Translator(timeout=Timeout(30.0))

    global translation_dir
    translation_dir = os.path.dirname(os.path.abspath(sys.argv[0])) + '/app/src/main/res/'

    # TODO - replace optparse with argparse
    parser = OptionParser()

    parser.add_option('-t', "--check", action="store_true", default=False,
                      dest="check", help="Check all language files for missing or empty strings and use Google translate on them. Write out the file with strings ordered as in the default file. Any new strings found in the non-default language files are discarded. Any strings deleted from the default file are deleted from the language files.")
    parser.add_option('-F', "--flag", default="",
                      dest="flag", help="Requires list of strings in one parameter. Flag the listed strings as changed so they are re-translated with the check option. This parameter also runs the check process even if it was not requested.")
    parser.add_option('-l', "--listkeys", action="store_true", default=False,
                      dest="listkeys", help="List all keys and strings from a language file. " \
                        "Show default language if language option is omitted.")
    parser.add_option('-i', "--listlanguages", action="store_true", default=False,
                      dest="listlangs", help="List all supported languages")
    parser.add_option('-L', "--language", metavar="LANGUAGE", default=None,
                      dest="language", help="The language to show keys/values for.")
    parser.add_option('-d','--datadir', metavar="DIR", default=None,
                      dest="datadir", help=("The location of the language directories. [default: '" + translation_dir + "']"))

    # If no option is supplied go to help
    if len(sys.argv) == 1:
        sys.argv.append("-h")

    opts, args = parser.parse_args()

    if opts.datadir:
        translation_dir = opts.datadir
        print("Using translation directory '%s'" % translation_dir)

    if opts.listlangs:
        listLanguages()
        sys.exit(0)
    elif opts.check or opts.flag:
        keylist = opts.flag.split()
        checkTranslations(keylist)
        sys.exit(0)
    elif opts.listkeys:
        lang = "default"
        if opts.language:
            lang = opts.language
        listKeys(lang)
        sys.exit(0)

    sys.exit(0)

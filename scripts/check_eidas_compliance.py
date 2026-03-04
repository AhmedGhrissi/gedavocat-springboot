#!/usr/bin/env python3
"""
Script de Vérification Conformité eIDAS

Vérifie automatiquement les exigences eIDAS :
- TSA qualifiée configurée
- Format d'archivage ASIC-E
- Signatures XAdES-LTA
- Rétention légale 30+ ans
"""

import requests
import sys
import json
from datetime import datetime

def check_eidas_compliance(api_url="http://localhost:8092"):
    """Vérifie la conformité eIDAS via l'API."""
    
    print("✍️ Vérification Conformité eIDAS")
    print("=" * 35)
    
    try:
        response = requests.get(f"{api_url}/api/compliance/eidas", timeout=30)
        
        if response.status_code == 200:
            data = response.json()
            score = data.get('score', 0)
            max_score = data.get('maxScore', 20)
            compliant = data.get('compliant', False)
            
            print(f"Score eIDAS: {score}/{max_score}")
            print(f"Conforme: {'✅ OUI' if compliant else '❌ NON'}")
            
            # Critères réussis
            passed = data.get('passedCriteria', [])
            if passed:
                print("\n✅ Critères Conformes:")
                for criteria in passed:
                    print(f"  • {criteria}")
            
            # Critères en échec
            failed = data.get('failedCriteria', [])
            if failed:
                print("\n❌ Points d'Amélioration:")
                for criteria in failed:
                    print(f"  • {criteria}")
            
            return compliant
            
        else:
            print(f"❌ Erreur API: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ Erreur vérification eIDAS: {e}")
        return False

if __name__ == "__main__":
    compliant = check_eidas_compliance()
    sys.exit(0 if compliant else 1)
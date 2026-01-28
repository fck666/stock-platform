import logging
import re
import requests
from bs4 import BeautifulSoup
import pandas as pd
from datetime import datetime

log = logging.getLogger(__name__)

def fetch_stooq_fundamentals(session: requests.Session, stooq_symbol: str, timeout: float, user_agent: str) -> dict:
    """
    Fetch market cap and shares outstanding from stooq.com/q/?s={symbol}
    """
    url = f"https://stooq.com/q/?s={stooq_symbol}"
    headers = {"User-Agent": user_agent}
    
    resp = session.get(url, headers=headers, timeout=timeout)
    resp.raise_for_status()
    
    soup = BeautifulSoup(resp.text, "html.parser")
    
    # Stooq's layout is messy. We look for text like "Market value" or "Liczba akcji"
    # English: "Market value", "Number of shares"
    # Polish: "Kapitalizacja", "Liczba akcji"
    
    res = {"market_cap": None, "shares_outstanding": None}
    
    def parse_stooq_num(text: str) -> float | None:
        if not text: return None
        text = text.strip().replace(" ", "").replace(",", "")
        # Handle multipliers: m (million), g (billion/milliard)
        multiplier = 1.0
        if text.lower().endswith("m"):
            multiplier = 1_000_000.0
            text = text[:-1]
        elif text.lower().endswith("g"):
            multiplier = 1_000_000_000.0
            text = text[:-1]
        elif text.lower().endswith("k"):
            multiplier = 1_000.0
            text = text[:-1]
            
        try:
            return float(text) * multiplier
        except ValueError:
            return None

    # Search in all <td> tags
    for td in soup.find_all("td"):
        txt = td.get_text().strip()
        if "Market value" in txt or "Kapitalizacja" in txt:
            val_td = td.find_next_sibling("td")
            if val_td:
                res["market_cap"] = parse_stooq_num(val_td.get_text())
        elif "Number of shares" in txt or "Liczba akcji" in txt:
            val_td = td.find_next_sibling("td")
            if val_td:
                res["shares_outstanding"] = parse_stooq_num(val_td.get_text())
                
    return res

def fetch_stooq_operations(session: requests.Session, stooq_symbol: str, timeout: float, user_agent: str) -> pd.DataFrame:
    """
    Fetch dividends and splits from stooq.com/q/m/?s={symbol}
    """
    url = f"https://stooq.com/q/m/?s={stooq_symbol}"
    headers = {"User-Agent": user_agent}
    
    resp = session.get(url, headers=headers, timeout=timeout)
    resp.raise_for_status()
    
    soup = BeautifulSoup(resp.text, "html.parser")
    table = soup.find("table", {"id": "fth1"})
    
    if not table:
        log.info("No operations table found for %s", stooq_symbol)
        return pd.DataFrame()
    
    records = []
    rows = table.find("tbody").find_all("tr")
    
    # Polish months mapping if needed, but stooq.com (English) uses abbreviations like 'pią, 20 maj 2022'
    # Wait, the snippet showed 'pią, 20 maj 2022' which is Polish (piątek, maj).
    # Stooq English usually uses 'Fri, 20 May 2022'.
    # Let's handle both or try to force English by cookie if possible.
    # Actually, let's just parse it robustly.
    
    month_map = {
        'sty': 1, 'lut': 2, 'mar': 3, 'kwi': 4, 'maj': 5, 'cze': 6,
        'lip': 7, 'sie': 8, 'wrz': 9, 'paź': 10, 'lis': 11, 'gru': 12,
        'jan': 1, 'feb': 2, 'apr': 4, 'may': 5, 'jun': 6,
        'jul': 7, 'aug': 8, 'sep': 9, 'oct': 10, 'nov': 11, 'dec': 12
    }

    for tr in rows:
        cols = tr.find_all("td")
        if len(cols) < 3:
            continue
            
        date_text = cols[0].get_text().strip() # e.g. "pią, 20 maj 2022" or "Fri, 20 May 2022"
        event_text = cols[1].get_text().strip() # e.g. "Dividend 0.47%"
        nominal_text = cols[2].get_text().strip() # e.g. "1.6"
        
        # Parse date
        # Remove day name prefix (e.g. "pią, " or "Fri, ")
        clean_date = re.sub(r'^[a-zA-Ząćęłńóśźż]{3},\s*', '', date_text)
        parts = clean_date.split(' ')
        if len(parts) == 3:
            day = int(parts[0])
            month_str = parts[1].lower()[:3]
            month = month_map.get(month_str, 1)
            year = int(parts[2])
            ex_date = datetime(year, month, day).date()
        else:
            continue
            
        # Parse nominal
        try:
            amount = float(nominal_text)
        except ValueError:
            amount = None
            
        op_type = "Dividend"
        if "Split" in event_text:
            op_type = "Split"
            
        records.append({
            "ex_date": ex_date,
            "type": op_type,
            "amount": amount,
            "raw_text": event_text
        })
        
    return pd.DataFrame.from_records(records)

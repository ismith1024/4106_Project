package featureEngineering;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class DailyData {
	public int year;
	public int month;
	public int day;
	public Date date;
	
	//internal factors
	public HashMap<String, Double> p_e;
	public HashMap<String, Double> div_yld;
	public HashMap<String, Double> ma200;
	public HashMap<String, Double> ma20;
	public HashMap<String, Double> beta;
	public HashMap<String, Double> volume;
	public HashMap<String, Double> relStr;
	public HashMap<String, Double> price;
	public HashMap<String, Double> earnings;
	public HashMap<String, Double> divs;
	public HashMap<String, Double> returns; //non-discounted, non dividend 200-day rate of return extrapolated from linear regression of log growth
	
	public HashMap<String, String> classes;
			
	//external factors
	public double tsx;
	public double bankRate;
	public double cpi;
	public double cadUSD;
	public double oilUSD;
	public double goldUSD;
	public double employment;
	public double householdGDP;
	public double indExpGDP;
	public double[] features;
	
	public static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd"); ; 
	
	public DailyData(){
		p_e = new HashMap<>();
		div_yld = new HashMap<>();
		ma200 = new HashMap<>();
		ma20 = new HashMap<>();
		beta = new HashMap<>();
		volume = new HashMap<>();
		relStr = new HashMap<>();
		price = new HashMap<>();
		earnings = new HashMap<>();
		divs = new HashMap<>();
		returns = new HashMap<>();
		
		classes = new HashMap<>();
	}
	
	public DailyData(int y, int m, int d){
		year = y;
		month = m;
		day = d;
		date = new Date(y - 1900, m -1, d);
		
		p_e = new HashMap<>();
		div_yld = new HashMap<>();
		ma200 = new HashMap<>();
		ma20 = new HashMap<>();
		beta = new HashMap<>();
		volume = new HashMap<>();
		relStr = new HashMap<>();
		price = new HashMap<>();
		earnings = new HashMap<>();
		divs = new HashMap<>();
		
		classes = new HashMap<>();
		returns = new HashMap<>();
		
		
	}
	
	public void set(String tick, double pe, double dv, double m200, double m20, double bet, double vol, double rel  ){
		p_e.put(tick, pe);
		div_yld.put(tick, dv);
		ma200.put(tick, m200);
		ma20.put(tick, m20);
		beta.put(tick, bet);
		volume.put(tick, vol);
		relStr.put(tick, rel);		
	}

	@Override
	public String toString(){
		String ret = "Date: " + df.format(date);
		for(String s: new String[]{"BMO", "BNS", "CP", "CNR", "MFC", "SLF", "ENB", "TRP"}){
			ret += s + " " + price.get(s);
		}
		
		return ret;
	}
	
	public String returnString(){
		String ret = "Date: " + df.format(date);
		for(String s: new String[]{"BMO", "BNS", "CP", "CNR", "MFC", "SLF", "ENB", "TRP"}){
			ret += s + " " + returns.get(s);
		}
		
		return ret;
	}
	
	public int CompareTo(DailyData d){
		return(this.date.compareTo(d.date));
	}
	
	public void setTsx(double tsx) {
		this.tsx = tsx;
	}

	public void setBankRate(double bankRate) {
		this.bankRate = bankRate;
	}

	public void setCpi(double cpi) {
		this.cpi = cpi;
	}

	public void setCadUSD(double cadUSD) {
		this.cadUSD = cadUSD;
	}

	public void setOilUSD(double oilUSD) {
		this.oilUSD = oilUSD;
	}

	public void setGoldUSD(double goldUSD) {
		this.goldUSD = goldUSD;
	}

	public void setEmployment(double employment) {
		this.employment = employment;
	}

	public void setHouseholdGDP(double householdGDP) {
		this.householdGDP = householdGDP;
	}

	public void setIndExpGDP(double indExpGDP) {
		this.indExpGDP = indExpGDP;
	}
	
	
}

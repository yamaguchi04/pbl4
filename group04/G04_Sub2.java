package group04;
import robocode.*;
//import java.awt.Color;

// API help : http://robocode.sourceforge.net/docs/robocode/robocode/Robot.html

/**
 * Neo - a robot by (your name here)
 */
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.TeamRobot;
import robocode.MessageEvent;
import robocode.TeamRobot;
import static robocode.util.Utils.normalRelativeAngleDegrees;

import java.awt.*;
import java.io.IOException;

import java.awt.Color;
import java.awt.geom.*;
import java.util.*;


public class G04_Sub2 extends TeamRobot {

	
	/**
	 * run: SnippetBot's default behavior
	 */
	Hashtable targets;				//all enemies are stored in the hashtable
	Enemy target;					//our current enemy
	final double PI = Math.PI;		//just a constant
	int direction = 1;				//direction we are heading... 1 = forward, -1 = backwards
	int enteam = 3;					//enemy team's living robot
	double firePower;				//the power of the shot we will be using
	double midpointstrength = 0;	//The strength of the gravity point in the middle of the field
	int midpointcount = 0;			//Number of turns since that strength was changed.
	public void run() {
	
		targets = new Hashtable();
		target = new Enemy();
		target.energy = -1;
		target.distance = 100000;						//initialise the distance so that we can select a target
		setColors(Color.red,Color.blue,Color.green);	//sets the colours of the robot
		//the next two lines mean that the turns of the robot, gun and radar are independant
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		turnRadarRightRadians(2*PI);					//turns the radar right around to get a view of the field

		// Prepare RobotColors object
		RobotColors c = new RobotColors();

		c.bodyColor = Color.blue;
		c.gunColor = Color.red;
		c.radarColor = Color.red;
		c.scanColor = Color.yellow;
		c.bulletColor = Color.green;

		// Set the color of this robot containing the RobotColors
		setBodyColor(c.bodyColor);
		setGunColor(c.gunColor);
		setRadarColor(c.radarColor);
		setScanColor(c.scanColor);
		setBulletColor(c.bulletColor);
		try {
			// Send RobotColors object to our entire team
			broadcastMessage(c);
		} catch (IOException ignored) {}
		// Normal behavior
		while (true) {
			antiGravMove();					//Move the bot
			doFirePower();					//select the fire power to use
			doScanner();					//Oscillate the scanner over the bot
			doGun();
			out.println(target.distance);	//move the gun to predict where the enemy will be
			fire(firePower);
			execute();						//execute all commands
		}
	}

	/**
	 * onScannedRobot:  What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		Enemy en;
		if (targets.containsKey(e.getName())) {
			en = (Enemy)targets.get(e.getName());
		} else {
			en = new Enemy();
			targets.put(e.getName(),en);
		}
		// Don't fire on teammates
		if (isTeammate(e.getName())) {
			en.teammate = true;
		}
		en.dummy = dummyChecker(e.getName());
		//the next line gets the absolute bearing to the point where the bot is
		double absbearing_rad = (getHeadingRadians()+e.getBearingRadians())%(2*PI);
		//this section sets all the information about our target
		en.name = e.getName();
		double h = normaliseBearing(e.getHeadingRadians() - en.heading);
		h = h/(getTime() - en.ctime);
		en.changehead = h;
		en.x = getX()+Math.sin(absbearing_rad)*e.getDistance(); //works out the x coordinate of where the target is
		en.y = getY()+Math.cos(absbearing_rad)*e.getDistance(); //works out the y coordinate of where the target is
		en.bearing = e.getBearingRadians();
		en.heading = e.getHeadingRadians();
		en.ctime = getTime();				//game time at which this scan was produced
		en.speed = e.getVelocity();
		en.distance = e.getDistance();	
		en.live = true;
		en.energy = e.getEnergy();
		if (en.teammate == false &&  en.dummy == false &&
			((en.energy>target.energy)||(en.energy<20 && (en.energy<target.energy))) &&
			((en.distance < target.distance)||(target.live == false))) {
			target = en;
		}
		if(enteam == 0 && en.dummy){
			//target = en;
		}
		try {
			 //Send enemy object to our entire team
			broadcastMessage(en);
		} catch (IOException ignored) {}
	}
	
	public void generateEnemy(Enemy en){
		Enemy newEn;
		if (targets.containsKey(en.name)) {
			newEn = (Enemy)targets.get(en.name);
		} else {
			newEn = new Enemy();
			targets.put(en.name,newEn);
		}
	}

	/**
	 * onHitByBullet:  Turn perpendicular to bullet path
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		//turnLeft(90 - e.getBearing());
	}

	void doFirePower() {
		firePower = 400/target.distance;//selects a bullet power based on our distance away from the target
		if (firePower > 3) {
			firePower = 3;
		}
	}
	
	boolean dummyChecker(String s){
		System.out.println(s);
		if	(s == "Walls (1)" || s == "Walls (2)" || s == "Walls (3)" ){
			return true;
		}
		return false;
	}
	
	public boolean isMatch(String str1, String str2) {
	    if(str1.matches(".*" + str2 + ".*")) {
	        return true;
	    }
	    else {
	        return false;
	    }
	}

	void antiGravMove() {
   		double xforce = 0;
	    double yforce = 0;
	    double force;
	    double ang;
	    GravPoint p;
		Enemy en;
    	Enumeration e = targets.elements();
	    //cycle through all the enemies.  If they are alive, they are repulsive.  Calculate the force on us
		while (e.hasMoreElements()) {
    	    en = (Enemy)e.nextElement();
			if (en.live) {
				p = new GravPoint(en.x,en.y, -1000);
		        force = p.power/Math.pow(getRange(getX(),getY(),p.x,p.y),2);
				if(en.name == target.name){
					force = -force*10;
				}
		        //Find the bearing from the point to us
		        ang = normaliseBearing(Math.PI/2 - Math.atan2(getY() - p.y, getX() - p.x)); 
		        //Add the components of this force to the total force in their respective directions
		        xforce += Math.sin(ang) * force;
		        yforce += Math.cos(ang) * force;
			}
	    }
	    
		/**The next section adds a middle point with a random (positive or negative) strength.
		The strength changes every 5 turns, and goes between -1000 and 1000.  This gives a better
		overall movement.**/
		midpointcount++;
		if (midpointcount > 5) {
			midpointcount = 0;
			midpointstrength = (Math.random() * 2000) - 1000;
		}
		p = new GravPoint(getBattleFieldWidth()/2, getBattleFieldHeight()/2, midpointstrength);
		force = p.power/Math.pow(getRange(getX(),getY(),p.x,p.y),1.5);
	    ang = normaliseBearing(Math.PI/2 - Math.atan2(getY() - p.y, getX() - p.x)); 
	    xforce += Math.sin(ang) * force;
	    yforce += Math.cos(ang) * force;
	   
	    /**The following four lines add wall avoidance.  They will only affect us if the bot is close 
	    to the walls due to the force from the walls decreasing at a power 3.**/
	    xforce += 5000/Math.pow(getRange(getX(), getY(), getBattleFieldWidth(), getY()), 3);
	    xforce -= 5000/Math.pow(getRange(getX(), getY(), 0, getY()), 3);
	    yforce += 5000/Math.pow(getRange(getX(), getY(), getX(), getBattleFieldHeight()), 3);
	    yforce -= 5000/Math.pow(getRange(getX(), getY(), getX(), 0), 3);
	    
	    //Move in the direction of our resolved force.
	    goTo(getX()-xforce,getY()-yforce);
	}
	
	/**Move towards an x and y coordinate**/
	void goTo(double x, double y) {
	    double dist = 20; 
	    double angle = Math.toDegrees(absbearing(getX(),getY(),x,y));
	    double r = turnTo(angle);
	    setAhead(dist * r);
	}


	/**Turns the shortest angle possible to come to a heading, then returns the direction the
	the bot needs to move in.**/
	int turnTo(double angle) {
	    double ang;
    	int dir;
	    ang = normaliseBearing(getHeading() - angle);
	    if (ang > 90) {
	        ang -= 180;
	        dir = -1;
	    }
	    else if (ang < -90) {
	        ang += 180;
	        dir = -1;
	    }
	    else {
	        dir = 1;
	    }
	    setTurnLeft(ang);
	    return dir;
	}

	/**keep the scanner turning**/
	void doScanner() {
		setTurnRadarLeftRadians(2*PI);
	}
	
	/**Move the gun to the predicted next bearing of the enemy**/
	void doGun() {
		long time = getTime() + (int)Math.round((getRange(getX(),getY(),target.x,target.y)/(20-(3*firePower))));
		Point2D.Double p = target.guessPosition(time);
		
		//offsets the gun by the angle to the next shot based on linear targeting provided by the enemy class
		double gunOffset = getGunHeadingRadians() - (Math.PI/2 - Math.atan2(p.y - getY(), p.x - getX()));
		setTurnGunLeftRadians(normaliseBearing(gunOffset));
	}
	

	//if a bearing is not within the -pi to pi range, alters it to provide the shortest angle
	double normaliseBearing(double ang) {
		if (ang > PI)
			ang -= 2*PI;
		if (ang < -PI)
			ang += 2*PI;
		return ang;
	}
	
	//if a heading is not within the 0 to 2pi range, alters it to provide the shortest angle
	double normaliseHeading(double ang) {
		if (ang > 2*PI)
			ang -= 2*PI;
		if (ang < 0)
			ang += 2*PI;
		return ang;
	}
	
	//returns the distance between two x,y coordinates
	public double getRange( double x1,double y1, double x2,double y2 )
	{
		double xo = x2-x1;
		double yo = y2-y1;
		double h = Math.sqrt( xo*xo + yo*yo );
		return h;	
	}
	
	//gets the absolute bearing between to x,y coordinates
	public double absbearing( double x1,double y1, double x2,double y2 )
	{
		double xo = x2-x1;
		double yo = y2-y1;
		double h = getRange( x1,y1, x2,y2 );
		if( xo > 0 && yo > 0 )
		{
			return Math.asin( xo / h );
		}
		if( xo > 0 && yo < 0 )
		{
			return Math.PI - Math.asin( xo / h );
		}
		if( xo < 0 && yo < 0 )
		{
			return Math.PI + Math.asin( -xo / h );
		}
		if( xo < 0 && yo > 0 )
		{
			return 2.0*Math.PI - Math.asin( -xo / h );
		}
		return 0;
	}

	/**
	 * onMessageReceived:  What to do when our leader sends a message
	 */
	public void onMessageReceived(MessageEvent e) {
		// Fire at a point
		if (e.getMessage() instanceof Enemy) {
			Enemy en = (Enemy)e.getMessage();
			generateEnemy(en);
		} // Set our colors
		else if (e.getMessage() instanceof RobotColors) {
			RobotColors c = (RobotColors) e.getMessage();

			setBodyColor(c.bodyColor);
			setGunColor(c.gunColor);
			setRadarColor(c.radarColor);
			setScanColor(c.scanColor);
			setBulletColor(c.bulletColor);
		}
	}
	
	
	public void onRobotDeath(RobotDeathEvent e) {
		Enemy en = (Enemy)targets.get(e.getName());
		en.live = false;	
		if(en.dummy == false && en.teammate == false){
			enteam--;
		}
	}	
}


class Enemy implements java.io.Serializable {
	/*
	 * ok, we should really be using accessors and mutators here,
	 * (i.e getName() and setName()) but life's too short.
	 */
	String name;
	public double bearing,heading,speed,x,y,distance,changehead,energy;
	public long ctime; 		//game time that the scan was produced
	public boolean live; 	//is the enemy alive?
	public boolean teammate = false;
	public boolean dummy = false;
	public Point2D.Double guessPosition(long when) {
		double diff = when - ctime;
		double newY = y + Math.cos(heading) * speed * diff;
		double newX = x + Math.sin(heading) * speed * diff;
		
		return new Point2D.Double(newX, newY);
	}
}

/**Holds the x, y, and strength info of a gravity point**/
class GravPoint implements java.io.Serializable {
    public double x,y,power;
    public GravPoint(double pX,double pY,double pPower) {
        x = pX;
        y = pY;
        power = pPower;
    }
}


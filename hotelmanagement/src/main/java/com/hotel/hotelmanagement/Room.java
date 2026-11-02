package com.hotel.hotelmanagement;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name="rooms")
public class Room {
	
@Id
@GeneratedValue(strategy=GenerationType.IDENTITY)
private Long id;
@Column(unique = true,nullable=false)
private String roomNumber;
private String roomType;
private double pricePerNight;
private boolean available =true;

public Room() {}
public Long getId() {
	return id;
}
public void setId(Long id) {
    this.id = id;
}
public String getRoomNumber() {
	return roomNumber;
}
public void setRoomNumber(String roomNumber) {
	this.roomNumber=roomNumber;
}
public String getRoomType() {
	return roomType;
}
public void setRoomType(String roomType) {
	this.roomType=roomType;
}
public double getPricePerNight() {
	return pricePerNight;
}
public void setPricePerNight(double pricePerNight) {
	this.pricePerNight=pricePerNight;
}
public boolean isAvailable() {
	return available;
}
public void setAvailable(boolean available ) {
	this.available=available;
}

}

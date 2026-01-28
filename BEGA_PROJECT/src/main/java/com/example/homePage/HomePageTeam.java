package com.example.homepage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor
public class HomePageTeam {

	@Id
    @Column(name = "team_id")
    private String teamId; 
	
	@Column(name = "team_name")
    private String teamName; 
    
    @Column(name = "team_short_name")
    private String teamShortName; 
}

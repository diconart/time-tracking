package md.dipro.worktime.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    protected Project() { }

    public Project(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
